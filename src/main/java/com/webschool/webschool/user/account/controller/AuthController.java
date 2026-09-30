package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.security.AuthenticationUtils;
import com.webschool.webschool.user.account.dto.RegisterDto;
import com.webschool.webschool.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    private final UserDetailsService userDetailsService;
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
    public String register(@ModelAttribute RegisterDto registerDto, HttpServletRequest request, Model model) {
        try {
            userService.register(registerDto);
            autoLogin(registerDto.getUsername(), request);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/register";
        }
    }

    // 가입 직후 바로 로그인 상태로 만들기 - 세션에 SecurityContext를 직접 심어야
    // 다음 요청(리다이렉트)부터 인증된 사용자로 인식된다(그냥 SecurityContextHolder만
    // 채우면 이번 요청 스레드에서만 유효하고 세션엔 저장되지 않는다).
    private void autoLogin(String username, HttpServletRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
