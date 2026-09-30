package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.util.ClientIpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

// IP당 로그인 실패 횟수 제한(보안 점검 M4, 2026-09-30). 계정별 5회 잠금(LoginAttemptService)만으로는
// 계정을 바꿔 가며 흔한 비밀번호를 하나씩 넣어보는 시도(password spraying)를 막지 못한다.
// 실패만 센다(LoginFailureHandler가 recordFailure 호출) - 학교처럼 여러 학생이 같은 공인 IP를 쓰는
// 환경에서 정상 로그인까지 막지 않도록. 한도에 걸리면 비밀번호 검증(BCrypt) 전에 여기서 돌려보낸다.
@Component
@RequiredArgsConstructor
public class LoginThrottleFilter extends OncePerRequestFilter {

    static final int MAX_FAILURES_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final RateLimiter rateLimiter;

    public void recordFailure(HttpServletRequest request) {
        rateLimiter.tryAcquire(key(request), Integer.MAX_VALUE, WINDOW);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (rateLimiter.isExhausted(key(request), MAX_FAILURES_PER_IP, WINDOW)) {
            response.sendRedirect(request.getContextPath() + "/login?throttled=true");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String key(HttpServletRequest request) {
        return "login-fail-ip:" + ClientIpUtils.getClientIp(request);
    }
}
