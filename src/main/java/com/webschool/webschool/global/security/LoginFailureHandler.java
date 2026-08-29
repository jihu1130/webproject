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
// (5회 실패 시 15분 잠금).
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?locked=true");
            return;
        }
        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            loginAttemptService.recordFailure(username.trim());
        }
        response.sendRedirect(request.getContextPath() + "/login?error=true");
    }
}
