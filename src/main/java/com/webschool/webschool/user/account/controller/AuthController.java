package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.security.AuthenticationUtils;
import com.webschool.webschool.global.security.jwt.JwtService;
import com.webschool.webschool.user.account.dto.RegisterDto;
import com.webschool.webschool.user.service.UserInputValidator;
import com.webschool.webschool.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

// 로그인/회원가입 화면과 아이디 중복확인. 2026-09-28 파일 정리 때 나머지 화면을 분리함:
// 온보딩(/school-setup, /email-setup, /password-setup, /verify-email) → OnboardingController,
// 계정 찾기(/find-username, /forgot-password, /reset-password) → AccountRecoveryController,
// 마이페이지(/mypage/**) → MyPageController.
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    // 구글 OAuth 클라이언트 등록(client-id/secret)이 안 돼 있으면 이 빈 자체가 없다(SecurityConfig
    // 참고) - 로그인/회원가입 화면에 "구글로 로그인" 버튼을 보여줄지 여기서 같은 방식으로 판단한다.
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    // 수정사항.md 지적 - 이미 로그인된 상태에서 /login·/register에 직접 들어가면(주소창 입력,
    // 즐겨찾기, 뒤로가기) 네비바는 로그인 상태를 보여주면서 그 아래에 로그인/회원가입 폼이
    // 또 뜨는 문제가 있었다. 인증된 사용자는 두 페이지 모두 홈으로 돌려보낸다.
    @GetMapping("/login")
    public String loginPage(Model model, Authentication authentication) {
        if (AuthenticationUtils.isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("googleLoginEnabled", clientRegistrationRepositoryProvider.getIfAvailable() != null);
        return "user/login";
    }

    // 아이디 중복확인 API
    @GetMapping("/api/users/check-username")
    @ResponseBody
    public Map<String, Object> checkUsername(@RequestParam(required = false) String username) {
        if (username == null || username.isBlank()) {
            return Map.of("available", false, "message", "아이디를 입력해주세요.");
        }
        // 형식·길이·금지 아이디도 여기서 같이 알려준다 - 안 그러면 "사용 가능"이라고 해놓고 가입에서 거절된다.
        try {
            UserInputValidator.requireValidUsername(username);
        } catch (IllegalArgumentException e) {
            return Map.of("available", false, "message", e.getMessage());
        }

        boolean available = userService.isUsernameAvailable(username);
        return Map.of(
                "available", available,
                "message", available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."
        );
    }

    @GetMapping("/register")
    public String registerPage(Model model, Authentication authentication) {
        if (AuthenticationUtils.isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("registerDto", new RegisterDto());
        model.addAttribute("googleLoginEnabled", clientRegistrationRepositoryProvider.getIfAvailable() != null);
        return "user/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterDto registerDto, HttpServletRequest request,
                           HttpServletResponse response, Model model) {
        try {
            userService.register(registerDto);
            autoLogin(registerDto.getUsername(), request, response);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/register";
        }
    }

    // 가입 직후 바로 로그인 상태로 만들기 - 일반 로그인(LoginSuccessHandler)과 똑같이 JWT 쿠키를 내준다.
    // 예전엔 세션에 SecurityContext를 직접 심었는데, 그 로그인은 JWT를 거치지 않아서 관리자 정지나 토큰
    // 무효화가 걸리지 않았다(2026-10-07) - 이제 로그인 상태는 세션에 두지 않는다(SecurityConfig.securityContext()).
    private void autoLogin(String username, HttpServletRequest request, HttpServletResponse response) {
        String token = jwtService.generateToken(username);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.buildCookie(token, request.isSecure()).toString());
    }
}
