package com.webschool.webschool.global.security.login;

import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.security.captcha.CaptchaService;
import com.webschool.webschool.user.account.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 로그인 대기 중 캡차 통과(2026-10-12). 대기가 걸린 시도는 캡차를 통과해야만 비밀번호 확인으로 넘어간다.
class LoginThrottleFilterTest {

    private static final String IP = "203.0.113.7";

    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final KnownDeviceCookie knownDeviceCookie = mock(KnownDeviceCookie.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);
    private RateLimiter rateLimiter;
    private LoginThrottleFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
        filter = new LoginThrottleFilter(rateLimiter, loginAttemptService, knownDeviceCookie, captchaService);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setRemoteAddr(IP);
        request.setParameter("username", "student");
        return request;
    }

    private void waitIs(long seconds) {
        when(loginAttemptService.waitSecondsBeforeNextAttempt(anyString(), anyString(), anyBoolean())).thenReturn(seconds);
    }

    @Test
    void notWaiting_passesWithoutAskingCaptcha() throws Exception {
        waitIs(0);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(loginRequest(), response, chain);

        assertNotNull(chain.getRequest());
        verify(captchaService, never()).verify(any(MockHttpServletRequest.class), anyString());
    }

    // 캡차가 꺼져 있거나 안 풀었으면 예전과 똑같이 기다려야 한다.
    @Test
    void waiting_withoutCaptcha_isSentBackToWait() throws Exception {
        waitIs(30);
        when(captchaService.verify(any(MockHttpServletRequest.class), anyString())).thenReturn(false);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(loginRequest(), response, chain);

        assertNull(chain.getRequest());
        assertEquals("/login?locked=true&wait=30", response.getRedirectedUrl());
    }

    @Test
    void waiting_withPassedCaptcha_goesOnToPasswordCheck() throws Exception {
        waitIs(1800);
        when(captchaService.verify(any(MockHttpServletRequest.class), anyString())).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(loginRequest(), response, chain);

        assertNotNull(chain.getRequest());
        assertNull(response.getRedirectedUrl());
    }

    // IP당 한도(15분 30회 실패)는 캡차로도 못 넘는다 - 캡차를 계속 풀어 가며 대입하는 것의 상한.
    @Test
    void ipWideLimit_isNotBypassedByCaptcha() throws Exception {
        MockHttpServletRequest request = loginRequest();
        for (int i = 0; i < LoginThrottleFilter.MAX_FAILURES_PER_IP; i++) {
            filter.recordFailure(request);
        }
        when(captchaService.verify(any(MockHttpServletRequest.class), anyString())).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals("/login?throttled=true", response.getRedirectedUrl());
    }
}
