package com.webschool.webschool.global.security;

import com.webschool.webschool.user.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 로그인 실패 사유에 따라 다른 안내로 보낸다 - 계정이 이미 잠긴 상태(LockedException, 비밀번호
// 검증 전 단계에서 던져짐)라면 실패 횟수를 더 세지 않고 잠금 안내로만 보내고, 그 외 일반적인
// 비밀번호 오류는 기존과 동일하게 처리하되 LoginAttemptService로 실패 횟수를 센다
// (5회 실패 시 5분 잠금).
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String username = request.getParameter("username");
        if (exception instanceof LockedException) {
            redirectLocked(request, response, username);
            return;
        }
        if (username == null || username.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login?error=true");
            return;
        }
        int attempts = loginAttemptService.recordFailure(username.trim());
        if (attempts >= LoginAttemptService.MAX_ATTEMPTS) {
            // 이번 실패로 막 잠긴 경우 - LockedException은 그 다음 시도부터 던져지므로
            // 지금 이 요청에서 바로 잠금 안내로 보내야 한다.
            redirectLocked(request, response, username);
        } else if (attempts > 0) {
            int remaining = LoginAttemptService.MAX_ATTEMPTS - attempts;
            response.sendRedirect(request.getContextPath()
                    + "/login?error=true&attempts=" + attempts + "&remaining=" + remaining);
        } else {
            response.sendRedirect(request.getContextPath() + "/login?error=true");
        }
    }

    // 잠금 화면에 실제 남은 시간(분)을 함께 보여준다 - username을 못 구하면(이론상 발생하지
    // 않지만 방어적으로) 분 정보 없이 잠금 안내만 보낸다.
    private void redirectLocked(HttpServletRequest request, HttpServletResponse response, String username) throws IOException {
        if (username == null || username.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login?locked=true");
            return;
        }
        long minutes = loginAttemptService.getRemainingLockMinutes(username.trim());
        response.sendRedirect(request.getContextPath() + "/login?locked=true&minutes=" + minutes);
    }
}
