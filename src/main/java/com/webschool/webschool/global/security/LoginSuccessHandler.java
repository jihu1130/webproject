package com.webschool.webschool.global.security;

import com.webschool.webschool.user.service.LoginAttemptService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 로그인 성공 시 실패 카운터를 초기화한다. 기존 formLogin().defaultSuccessUrl("/", false)와
// 동작이 동일하도록(SavedRequestAwareAuthenticationSuccessHandler가 그 설정의 실제 구현체)
// 초기화해서 쓴다 - 초기화 로직 한 줄을 끼워 넣기 위해 직접 빈으로 등록.
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    @PostConstruct
    void init() {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws ServletException, IOException {
        loginAttemptService.recordSuccess(authentication.getName());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
