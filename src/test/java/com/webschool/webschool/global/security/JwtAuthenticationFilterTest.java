package com.webschool.webschool.global.security;

import com.webschool.webschool.user.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

// 세션 인증 → JWT 전체 교체(2026-09-14)의 핵심 요구사항(계획 문서 결정 4번) 회귀 테스트:
// 관리자가 계정을 강제 정지/탈퇴시키면, 이미 발급된 JWT를 들고 있어도 다음 요청부터 즉시
// 익명 처리돼야 한다 - 별도 revocation 블랙리스트 없이 CustomUserDetailsService를 매 요청
// 재사용해서 만족시키는 설계라, 그 재사용이 실제로 매 요청 일어나는지가 검증 포인트다.
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";

    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails activeUser(String username) {
        return User.builder().username(username).password("x").roles("USER").build();
    }

    @Test
    void validCookie_populatesSecurityContext() throws Exception {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("test1");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(JwtService.COOKIE_NAME, token)});
        when(userDetailsService.loadUserByUsername("test1")).thenReturn(activeUser("test1"));

        new JwtAuthenticationFilter(jwtService, userDetailsService).doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test1", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noCookie_staysAnonymous() throws Exception {
        JwtService jwtService = new JwtService(SECRET);
        when(request.getCookies()).thenReturn(null);

        new JwtAuthenticationFilter(jwtService, userDetailsService).doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void expiredOrTamperedCookie_staysAnonymous() throws Exception {
        JwtService issuer = new JwtService(SECRET);
        JwtService verifier = new JwtService("a-completely-different-secret-key-32bytes");
        String token = issuer.generateToken("test1");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(JwtService.COOKIE_NAME, token)});

        new JwtAuthenticationFilter(verifier, userDetailsService).doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void userDeactivatedAfterTokenIssued_isTreatedAsAnonymous() throws Exception {
        // 관리자 강제 정지/탈퇴 시나리오 - 토큰 자체는 여전히 서명/만료 검증을 통과하지만,
        // CustomUserDetailsService가 최신 DB 상태를 반영해 isEnabled()=false인 UserDetails를
        // 돌려준다(User.isDeleted()||!User.isActive()||패널티 중 하나라도 걸리면 이렇게 됨).
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("test1");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(JwtService.COOKIE_NAME, token)});
        UserDetails disabled = User.builder().username("test1").password("x").roles("USER").disabled(true).build();
        when(userDetailsService.loadUserByUsername("test1")).thenReturn(disabled);

        new JwtAuthenticationFilter(jwtService, userDetailsService).doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void userHardDeletedAfterTokenIssued_isTreatedAsAnonymous() throws Exception {
        // AccountHardDeleteService가 계정 행 자체를 지운 뒤(7일 경과 자진 탈퇴, CLAUDE.md 참고)
        // 남아있는 토큰으로 요청이 오면 UsernameNotFoundException이 나야 정상 - 이것도 500이
        // 아니라 조용히 익명 처리돼야 한다.
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("ghost");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(JwtService.COOKIE_NAME, token)});
        when(userDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("gone"));

        new JwtAuthenticationFilter(jwtService, userDetailsService).doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
