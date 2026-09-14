package com.webschool.webschool.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 세션 인증 → JWT 전체 교체(사용자 확정, 2026-09-14) - 구글 로그인도 폼 로그인(LoginSuccessHandler)과
// 동일하게 JWT 쿠키를 발급하고 홈으로 보낸다. CustomOAuth2UserService가 이미
// nameAttributeKey="username"으로 맞춰둬서 authentication.getName()이 로그인 방식과 무관하게
// 항상 내부 username을 반환하므로, 이 핸들러는 그 값을 그대로 재사용하면 된다(별도 분기 불필요).
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws ServletException, IOException {
        String token = jwtService.generateToken(authentication.getName());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.buildCookie(token, request.isSecure()).toString());
        response.sendRedirect("/");
    }
}
