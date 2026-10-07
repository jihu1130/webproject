package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.util.ClientIpUtils;
import com.webschool.webschool.user.account.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 로그인 실패 처리 - 실패 횟수를 "아이디 + 접속한 곳" 단위로 세고(LoginAttemptService), 5회부터는 다음
// 시도까지 기다려야 하는 시간을 안내한다. 계정 자체는 잠그지 않는다(2026-10-07, 클래스 주석은
// LoginAttemptService 참고). 대기 중인 시도는 LoginThrottleFilter가 비밀번호 검증 전에 돌려보내므로
// 여기까지 오지 않는다.
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final LoginThrottleFilter loginThrottleFilter;
    private final KnownDeviceCookie knownDeviceCookie;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        loginThrottleFilter.recordFailure(request);
        String username = request.getParameter("username");
        if (username == null || username.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login?error=true");
            return;
        }
        username = username.trim();
        LoginAttemptService.FailureResult result = loginAttemptService.recordFailure(
                username, ClientIpUtils.getClientIp(request), knownDeviceCookie.isPresentFor(request, username));
        if (result.blocked()) {
            response.sendRedirect(request.getContextPath() + "/login?locked=true&wait=" + result.waitSeconds());
        } else {
            int remaining = LoginAttemptService.FREE_ATTEMPTS - result.attempts();
            response.sendRedirect(request.getContextPath()
                    + "/login?error=true&attempts=" + result.attempts() + "&remaining=" + remaining);
        }
    }
}
