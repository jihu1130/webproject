package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.security.jwt.JwtService;
import com.webschool.webschool.global.util.ClientIpUtils;
import com.webschool.webschool.user.account.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 세션 인증 → JWT 전체 교체(사용자 확정, 2026-09-14) - 예전엔 SavedRequestAwareAuthenticationSuccessHandler로
// "원래 요청했던 페이지로 복귀"를 지원했지만, stateless 전환과 함께 사용자가 "항상 홈으로
// 단순화"를 택해서(OAuth2 로그인이 이미 이렇게 동작 중이던 것과 통일) 더 이상 필요 없다.
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;
    private final KnownDeviceCookie knownDeviceCookie;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws ServletException, IOException {
        String username = authentication.getName();
        loginAttemptService.recordSuccess(username, ClientIpUtils.getClientIp(request),
                knownDeviceCookie.isPresentFor(request, username));
        String token = jwtService.generateToken(username);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.buildCookie(token, request.isSecure()).toString());
        // 이 브라우저를 "아는 기기"로 표시 - 남이 이 아이디로 비밀번호를 틀려 제한이 걸려도 여기서는 로그인할 수 있다.
        knownDeviceCookie.issue(request, response, username);
        // 로그인 실패/잠금은 LoginAttemptService가 감사 로그로 남기지만 성공은 어디에도 안 남던 빈틈.
        log.info("로그인 성공(아이디/비밀번호) user={}", authentication.getName());
        response.sendRedirect("/");
    }
}
