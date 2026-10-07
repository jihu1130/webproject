package com.webschool.webschool.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 상태는 JWT 쿠키로만 판단하고, 세션에 들어 있는 SecurityContext는 믿지 않는다(2026-10-07).
// 예전엔 폼/구글 로그인이 로그인 상태를 세션에도 저장해서, JWT가 무효화돼도(다른 기기에서 로그아웃, 비밀번호 변경,
// 관리자 정지, 만료) 세션 쿠키만으로 계속 로그인돼 있었다 - 토큰 무효화(보안 점검 L2)가 실제 브라우저에서는
// 통하지 않던 구멍. SecurityConfig.securityContext() 설정이 빠지거나 기본값으로 돌아가면 이 테스트가 깨진다.
@SpringBootTest
class SessionIsNotALoginTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void securityContextInSession_doesNotAuthenticate() throws Exception {
        SecurityContext stale = new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                "someone", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, stale);

        mvc.perform(get("/mypage").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/users").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
