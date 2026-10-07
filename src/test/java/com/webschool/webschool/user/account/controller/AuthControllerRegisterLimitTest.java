package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.security.jwt.JwtService;
import com.webschool.webschool.user.account.dto.RegisterDto;
import com.webschool.webschool.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 회원가입 횟수 제한(2026-10-07) 회귀 테스트 - IP당 1시간 20건, "성공한 가입만" 센다.
@ExtendWith(MockitoExtension.class)
class AuthControllerRegisterLimitTest {

    private static final String SCHOOL_IP = "203.0.113.7";

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService, jwtService, new RateLimiter(), clientRegistrationRepositoryProvider);
        lenient().when(jwtService.generateToken(anyString())).thenReturn("token");
        lenient().when(jwtService.buildCookie(anyString(), anyBoolean()))
                .thenReturn(ResponseCookie.from(JwtService.COOKIE_NAME, "token").build());
    }

    private String register(String ip, Model model, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RegisterDto dto = new RegisterDto();
        dto.setUsername("student");
        return controller.register(dto, request, response, model);
    }

    private String register(String ip) {
        return register(ip, new ExtendedModelMap(), new MockHttpServletResponse());
    }

    @Test
    void twentyFirstRegistrationFromSameIp_isRejected_otherIpsUnaffected() {
        for (int i = 0; i < 20; i++) {
            assertEquals("redirect:/", register(SCHOOL_IP));
        }

        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals("user/register", register(SCHOOL_IP, model, response));

        assertNotNull(model.getAttribute("errorMessage"));
        // 막힌 요청은 계정을 만들지도, 로그인시키지도 않는다.
        verify(userService, times(20)).register(any());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));

        assertEquals("redirect:/", register("198.51.100.9"));
    }

    // 입력 오류로 실패한 제출은 세지 않는다 - 비밀번호 규칙에 걸려 여러 번 다시 내는 사용자가 막히면 안 된다.
    @Test
    void failedSubmissions_doNotCountTowardLimit() {
        doThrow(new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.")).when(userService).register(any());
        for (int i = 0; i < 50; i++) {
            assertEquals("user/register", register(SCHOOL_IP));
        }

        org.mockito.Mockito.reset(userService);
        assertEquals("redirect:/", register(SCHOOL_IP));
    }

    // 가입에 성공하면 일반 로그인과 같이 JWT 쿠키를 내준다(세션에 로그인 상태를 넣지 않는다).
    @Test
    void successfulRegistration_issuesJwtCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        register(SCHOOL_IP, new ExtendedModelMap(), response);

        assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }
}
