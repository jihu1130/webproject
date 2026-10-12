package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.security.captcha.CaptchaService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 회원가입 캡차(2026-10-12) - 캡차가 켜진 환경에서는 통과한 요청만 계정을 만든다.
@ExtendWith(MockitoExtension.class)
class AuthControllerCaptchaTest {

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
    @Mock private CaptchaService captchaService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService, jwtService, new RateLimiter(),
                clientRegistrationRepositoryProvider, captchaService);
        when(captchaService.isEnabled()).thenReturn(true);
        lenient().when(jwtService.generateToken(anyString())).thenReturn("token");
        lenient().when(jwtService.buildCookie(anyString(), anyBoolean()))
                .thenReturn(ResponseCookie.from(JwtService.COOKIE_NAME, "token").build());
    }

    private String register(Model model, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RegisterDto dto = new RegisterDto();
        dto.setUsername("student");
        return controller.register(dto, request, response, model);
    }

    @Test
    void failedCaptcha_createsNoAccount() {
        when(captchaService.verify(any(MockHttpServletRequest.class), anyString())).thenReturn(false);
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals("user/register", register(model, response));

        assertNotNull(model.getAttribute("errorMessage"));
        verify(userService, never()).register(any());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void passedCaptcha_registers() {
        when(captchaService.verify(any(MockHttpServletRequest.class), anyString())).thenReturn(true);

        assertEquals("redirect:/", register(new ExtendedModelMap(), new MockHttpServletResponse()));

        verify(userService).register(any());
    }
}
