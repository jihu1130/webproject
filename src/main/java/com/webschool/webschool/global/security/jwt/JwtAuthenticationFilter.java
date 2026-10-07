package com.webschool.webschool.global.security.jwt;

import com.webschool.webschool.global.logging.RequestLoggingFilter;
import com.webschool.webschool.user.account.service.AccountUserDetails;
import com.webschool.webschool.user.account.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
// 결정 4번 참고). 같은 재조회로 로그아웃/비밀번호 변경 이전에 발급된 토큰도 걸러낸다
// (User.tokensInvalidBefore, 2026-10-07). 검증 실패(토큰 없음/만료/변조/유저 사라짐)는 예외를 던지지 않고 그냥 다음
// 필터로 넘긴다 - SecurityConfig의 authorizeHttpRequests 규칙이 나머지를 처리한다.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    // SecurityConfig.securityContext()에 지정한 것과 같은 종류 - 요청 속성에만 저장하므로 인스턴스가 달라도 같은 곳을 본다.
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        readTokenCookie(request)
                .flatMap(jwtService::parse)
                .ifPresent(claims -> authenticate(claims, request, response));

        filterChain.doFilter(request, response);
    }

    private void authenticate(JwtService.TokenClaims claims, HttpServletRequest request, HttpServletResponse response) {
        String username = claims.username();
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                return;
            }
            // 로그아웃했거나 비밀번호를 바꾸기 전에 발급된 토큰(보안 점검 L2) - 서명과 만료는 멀쩡해도 익명 처리.
            if (userDetails instanceof AccountUserDetails account && account.isTokenRevoked(claims.issuedAt())) {
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            // SecurityContextHolder만 채우지 않고 저장소(요청 속성)에도 저장한다. 안 하면 SessionManagementFilter가
            // "이 요청에서 방금 로그인했다"고 오인해 세션 고정 보호를 매 요청 다시 실행하고, 그때마다 세션과
            // CSRF 토큰이 새로 발급돼 화면에 그려진 토큰이 제출 시점엔 이미 무효가 된다(2026-09-16에 겪은 버그와
            // 같은 원리 - 로그인 상태를 세션에 두지 않게 바꾸면서 다시 나타나 2026-10-07에 이 방식으로 해결).
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            // 요청 로그(RequestLoggingFilter)가 요청이 끝난 뒤 "누가" 보낸 요청인지 찍을 수 있게 남기고,
            // 이 요청 안에서 나오는 다른 로그 줄에도 사용자가 찍히도록 MDC에 넣는다(MDC는
            // RequestLoggingFilter가 요청 끝에 비운다).
            request.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, username);
            MDC.put(RequestLoggingFilter.MDC_USER, username);
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
