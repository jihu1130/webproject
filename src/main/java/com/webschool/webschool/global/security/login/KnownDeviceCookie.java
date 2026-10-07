package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.security.jwt.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// "아는 기기" 쿠키 - 로그인에 성공한 브라우저에 남기는 서명된 표시(2026-10-07, 계정 잠금 DoS 대응).
// 남이 내 아이디로 비밀번호를 계속 틀려 로그인 제한이 걸려도, 이 쿠키를 가진 내 브라우저는 그 제한과
// 따로 계산된다(LoginAttemptService). 서명이 있어 위조할 수 없고, 훔쳐가도 로그인 권한은 없다 -
// 그 브라우저 몫의 실패 횟수가 따로 세어질 뿐이다. 로그인 요청에만 보내면 되므로 경로를 /login으로 좁혔다.
@Component
@RequiredArgsConstructor
public class KnownDeviceCookie {

    static final String NAME = "known_device";

    private final JwtService jwtService;

    public boolean isPresentFor(HttpServletRequest request, String username) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && jwtService.isKnownDeviceTokenFor(cookie.getValue(), username)) {
                return true;
            }
        }
        return false;
    }

    public void issue(HttpServletRequest request, HttpServletResponse response, String username) {
        ResponseCookie cookie = ResponseCookie.from(NAME, jwtService.generateKnownDeviceToken(username))
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/login")
                .maxAge(JwtService.KNOWN_DEVICE_EXPIRATION)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
