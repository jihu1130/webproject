package com.webschool.webschool.global.security;

import com.webschool.webschool.user.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

// 세션 인증 → JWT 전체 교체(사용자 확정, 2026-09-14). 요청마다 httpOnly 쿠키의 JWT를 읽어
// SecurityContext를 채운다 - CustomUserDetailsService.loadUserByUsername()을 매 요청 그대로
// 재사용하므로, 토큰 발급 이후 관리자가 계정을 정지/탈퇴시켰다면(active=false, deleted=true 등)
// 다음 요청부터 즉시 익명 처리된다(별도 revocation 블랙리스트 없이 요구사항을 만족 - 계획 문서
// 결정 4번 참고). 검증 실패(토큰 없음/만료/변조/유저 사라짐)는 예외를 던지지 않고 그냥 다음
// 필터로 넘긴다 - SecurityConfig의 authorizeHttpRequests 규칙이 나머지를 처리한다.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        readTokenCookie(request)
                .flatMap(jwtService::validateAndGetUsername)
                .ifPresent(username -> authenticate(username, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(String username, HttpServletRequest request) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException e) {
            // 토큰 발급 이후 계정이 완전히 삭제된 경우(AccountHardDeleteService) - 조용히 익명 유지
        }
    }

    private Optional<String> readTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (JwtService.COOKIE_NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue()).filter(v -> !v.isBlank());
            }
        }
        return Optional.empty();
    }
}
