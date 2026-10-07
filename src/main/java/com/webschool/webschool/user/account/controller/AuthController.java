package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.global.security.AuthenticationUtils;
import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.util.ClientIpUtils;
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

import java.time.Duration;
import java.util.Map;

// 로그인/회원가입 화면과 아이디 중복확인. 2026-09-28 파일 정리 때 나머지 화면을 분리함:
// 온보딩(/school-setup, /email-setup, /password-setup, /verify-email) → OnboardingController,
// 계정 찾기(/find-username, /forgot-password, /reset-password) → AccountRecoveryController,
// 마이페이지(/mypage/**) → MyPageController.
@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final String REGISTER_LIMIT_KEY = "register-ip:";
    private static final int REGISTER_LIMIT = 20;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    private final UserService userService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    // 구글 OAuth 클라이언트 등록(client-id/secret)이 안 돼 있으면 이 빈 자체가 없다(SecurityConfig
    // 참고) - 로그인/회원가입 화면에 "구글로 로그인" 버튼을 보여줄지 여기서 같은 방식으로 판단한다.
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    // 수정사항.md 지적 - 이미 로그인된 상태에서 /login·/register에 직접 들어가면(주소창 입력,
    // 즐겨찾기, 뒤로가기) 네비바는 로그인 상태를 보여주면서 그 아래에 로그인/회원가입 폼이
    // 또 뜨는 문제가 있었다. 인증된 사용자는 두 페이지 모두 홈으로 돌려보낸다.
    @GetMapping("/login")
    public String loginPage(Model model, Authentication authentication,
                            @RequestParam(required = false) String wait) {
        if (AuthenticationUtils.isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("loginWaitText", waitText(wait));
        model.addAttribute("googleLoginEnabled", clientRegistrationRepositoryProvider.getIfAvailable() != null);
        return "user/login";
    }

    // 로그인 대기 안내(/login?locked=true&wait=초)의 남은 시간 문구 - 주소창에서 바꿀 수 있는 값이라
    // 숫자가 아니거나 터무니없으면 "잠시"로만 보여준다.
    static String waitText(String waitSeconds) {
        if (waitSeconds == null || !waitSeconds.matches("[0-9]{1,5}")) {
            return "잠시";
        }
        int seconds = Integer.parseInt(waitSeconds);
        if (seconds <= 0) {
            return "잠시";
        }
        return seconds < 60 ? seconds + "초" : ((seconds + 59) / 60) + "분";
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
            // 가입 횟수 제한(2026-10-07) - 로그인/계정 찾기/문의에는 제한이 있었는데 가입에는 없어서 계정을
            // 무한정 만들 수 있었다. **성공한 가입만 센다**(오타로 여러 번 다시 제출하는 것까지 세면 정상 사용자가
            // 막힌다) - 그래서 먼저 한도 도달 여부만 보고, 가입이 끝난 뒤에 센다.
            // 한도가 넉넉한(IP당 1시간 20건) 이유: 학교에서는 한 반이 같은 공인 IP로 한꺼번에 가입한다.
            // 대량 생성을 늦추는 장치이지, 계정 몇 개를 만드는 것까지 막지는 못한다.
            String clientIp = ClientIpUtils.getClientIp(request);
            if (rateLimiter.isExhaustedForIp(REGISTER_LIMIT_KEY, clientIp, REGISTER_LIMIT, REGISTER_WINDOW)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "이 네트워크에서 가입이 너무 많이 이루어졌습니다. 잠시 후 다시 시도해주세요.");
            }
            userService.register(registerDto);
            rateLimiter.tryAcquireForIp(REGISTER_LIMIT_KEY, clientIp, REGISTER_LIMIT, REGISTER_WINDOW);
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
