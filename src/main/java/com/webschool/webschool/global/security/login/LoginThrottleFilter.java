package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.util.ClientIpUtils;
import com.webschool.webschool.user.account.service.LoginAttemptService;
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
    private static final String KEY_PREFIX = "login-fail-ip:";

    private final RateLimiter rateLimiter;
    private final LoginAttemptService loginAttemptService;
    private final KnownDeviceCookie knownDeviceCookie;

    public void recordFailure(HttpServletRequest request) {
        rateLimiter.tryAcquireForIp(KEY_PREFIX, ClientIpUtils.getClientIp(request), Integer.MAX_VALUE, WINDOW);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (rateLimiter.isExhaustedForIp(KEY_PREFIX, ClientIpUtils.getClientIp(request), MAX_FAILURES_PER_IP, WINDOW)) {
            response.sendRedirect(request.getContextPath() + "/login?throttled=true");
            return;
        }
        // 이 아이디로 이 접속한 곳에서 연속 실패해 대기 중이면 비밀번호를 확인하지 않고 돌려보낸다(2026-10-07).
        // 맞는 비밀번호를 넣어도 대기 중에는 통과시키지 않는다 - 통과시키면 대기가 대입 속도를 늦추지 못한다.
        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            username = username.trim();
            long wait = loginAttemptService.waitSecondsBeforeNextAttempt(username,
                    ClientIpUtils.getClientIp(request), knownDeviceCookie.isPresentFor(request, username));
            if (wait > 0) {
                response.sendRedirect(request.getContextPath() + "/login?locked=true&wait=" + wait);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
