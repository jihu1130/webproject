package com.webschool.webschool.global.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

// server.forward-headers-strategy: framework(운영/도커 설정)일 때 Spring Boot가 자동 등록하는
// ForwardedHeaderFilter를 TrustedProxyForwardedHeaderFilter로 교체한다(보안 점검 L3). Boot 자동 설정은
// 같은 타입의 필터 빈이 이미 있으면 물러나므로(@ConditionalOnMissingFilterBean) 이 빈만 남는다.
// 등록 순서/디스패처 타입은 Boot 기본값과 같게 맞췄다 - 이 설정이 없는 로컬(bootRun, 프록시 없음)에선
// 아무것도 등록되지 않고 getRemoteAddr()가 실제 접속 주소 그대로다.
@Configuration
public class ForwardedHeaderConfig {

    @Bean
    @ConditionalOnProperty(name = "server.forward-headers-strategy", havingValue = "framework")
    public FilterRegistrationBean<ForwardedHeaderFilter> trustedProxyForwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new TrustedProxyForwardedHeaderFilter());
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
