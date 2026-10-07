package com.webschool.webschool.global.security.csp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

// 요청마다 Content-Security-Policy용 nonce(1회용 임의 값)를 만들어 요청 속성에 둔다(보안 점검 L4).
// 템플릿의 인라인 <script>는 th:attr="nonce=${cspNonce}"로 이 값을 달고, CspHeaderWriter가 같은 값을
// 응답 헤더에 넣는다 - 브라우저는 nonce가 맞는 인라인 스크립트만 실행하므로, 새니타이저가 뚫려 본문에
// <script>나 on* 속성이 들어가도 실행되지 않는다. 에러 페이지로 포워드돼도 요청 속성은 그대로 남는다.
public class CspNonceFilter extends OncePerRequestFilter {

    public static final String NONCE_ATTRIBUTE = "cspNonce";

    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getAttribute(NONCE_ATTRIBUTE) == null) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            request.setAttribute(NONCE_ATTRIBUTE, Base64.getEncoder().encodeToString(bytes));
        }
        filterChain.doFilter(request, response);
    }
}
