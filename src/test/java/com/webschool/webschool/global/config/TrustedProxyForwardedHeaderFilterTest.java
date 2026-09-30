package com.webschool.webschool.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 보안 점검 L3(2026-09-30) - 운영 설정(forward-headers-strategy: framework)에서 쓰는 필터.
// IP는 신뢰 프록시 기준으로 고정하되, https/호스트 판단(구글 OAuth redirect_uri)은 기존과 같아야 한다.
class TrustedProxyForwardedHeaderFilterTest {

    private MockHttpServletRequest requestFromNginx() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 1.2.3.4"); // 6.6.6.6 = 클라이언트가 위조한 값
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("Host", "webschool.kro.kr");
        return request;
    }

    private HttpServletRequest runThrough(ForwardedHeaderFilter filter, MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void springDefaultFilterTrustsSpoofedLeftmostValue() throws Exception {
        // 수정 전 운영 동작 기록 - 기본 필터는 클라이언트가 넣은 첫 번째 값을 그대로 믿는다
        assertEquals("6.6.6.6", runThrough(new ForwardedHeaderFilter(), requestFromNginx()).getRemoteAddr());
    }

    @Test
    void usesAddressAddedByNginxAndKeepsHttpsHandling() throws Exception {
        HttpServletRequest seen = runThrough(new TrustedProxyForwardedHeaderFilter(), requestFromNginx());

        assertEquals("1.2.3.4", seen.getRemoteAddr());
        assertNull(seen.getHeader("X-Forwarded-For"));
        assertTrue(seen.isSecure());
        assertEquals("https", seen.getScheme());
        assertEquals("https://webschool.kro.kr/login", seen.getRequestURL().toString());
    }

    @Test
    void directClientCannotSpoofIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Forwarded-For", "6.6.6.6");

        assertEquals("8.8.8.8", runThrough(new TrustedProxyForwardedHeaderFilter(), request).getRemoteAddr());
    }
}
