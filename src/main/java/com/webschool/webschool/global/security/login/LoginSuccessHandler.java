package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.security.jwt.JwtService;
import com.webschool.webschool.user.account.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 세션 인증 → JWT 전체 교체(사용자 확정, 2026-09-14) - 예전엔 SavedRequestAwareAuthenticationSuccessHandler로
// "원래 요청했던 페이지로 복귀"를 지원했지만, stateless 전환과 함께 사용자가 "항상 홈으로
// 단순화"를 택해서(OAuth2 로그인이 이미 이렇게 동작 중이던 것과 통일) 더 이상 필요 없다.
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws ServletException, IOException {
        loginAttemptService.recordSuccess(authentication.getName());
        String token = jwtService.generateToken(authentication.getName());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.buildCookie(token, request.isSecure()).toString());
        response.sendRedirect("/");
    }
}
