package com.webschool.webschool.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

// 보안 점검 L3(2026-09-30) - 운영과 같은 설정(framework)에서 Spring Boot 기본 ForwardedHeaderFilter가
// 물러나고 TrustedProxyForwardedHeaderFilter 하나만 등록되는지. 둘 다 등록되면 기본 필터가 다시
// 위조된 X-Forwarded-For 값을 getRemoteAddr()에 덮어쓴다.
@SpringBootTest(properties = "server.forward-headers-strategy=framework")
class ForwardedHeaderConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void onlyTrustedProxyFilterIsRegistered() {
        List<Object> forwardedFilters = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                .map(FilterRegistrationBean::getFilter)
                .filter(f -> f instanceof ForwardedHeaderFilter)
                .map(f -> (Object) f)
                .toList();

        assertEquals(1, forwardedFilters.size(), forwardedFilters.toString());
        assertInstanceOf(TrustedProxyForwardedHeaderFilter.class, forwardedFilters.get(0));
        assertEquals(0, context.getBeansOfType(ForwardedHeaderFilter.class).size());
    }
}
