package com.webschool.webschool.global.config;

import com.webschool.webschool.global.util.ClientIpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

// Spring 기본 ForwardedHeaderFilter 대신 쓰는 필터(보안 점검 L3, 2026-09-30) - ForwardedHeaderConfig 참고.
// 기본 필터는 X-Forwarded-For의 첫 번째 값(클라이언트가 조작 가능)을 getRemoteAddr()에 덮어쓴다.
// 여기서는 ClientIpUtils.resolve()로 신뢰 프록시 기준의 실제 IP를 먼저 계산해 getRemoteAddr()로 고정하고,
// X-Forwarded-For는 숨긴 채 나머지(X-Forwarded-Proto/Host/Port/Prefix - https 판단, 구글 OAuth
// redirect_uri 생성에 필요)만 기존 필터에 그대로 맡긴다.
public class TrustedProxyForwardedHeaderFilter extends ForwardedHeaderFilter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = ClientIpUtils.resolve(request.getRemoteAddr(),
                Collections.list(request.getHeaders(X_FORWARDED_FOR)));
        super.doFilterInternal(new ResolvedClientIpRequest(request, clientIp), response, filterChain);
    }

    private static final class ResolvedClientIpRequest extends HttpServletRequestWrapper {

        private final String clientIp;

        ResolvedClientIpRequest(HttpServletRequest request, String clientIp) {
            super(request);
            this.clientIp = clientIp;
        }

        @Override
        public String getRemoteAddr() {
            return clientIp;
        }

        @Override
        public String getRemoteHost() {
            return clientIp;
        }

        @Override
        public String getHeader(String name) {
            return X_FORWARDED_FOR.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return X_FORWARDED_FOR.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(X_FORWARDED_FOR::equalsIgnoreCase);
            return Collections.enumeration(names);
        }
    }
}
