package com.webschool.webschool.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Swagger UI / OpenAPI 문서(springdoc, 2026-09-30 추가)는 전체 엔드포인트와 파라미터를 그대로
// 드러내므로 총관리자만 열람 가능해야 한다(SecurityConfig). 규칙 순서가 anyRequest().authenticated()
// 뒤로 밀리면 일반 로그인 사용자도 보게 되는데 컴파일 에러 없이 조용히 열리므로 테스트로 고정한다.
@SpringBootTest
class SwaggerAccessTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymous_isRedirectedToLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // SecurityConfig의 accessDeniedHandler는 /admin/** 밖의 403을 홈("/")으로 돌려보낸다.
    @Test
    @WithMockUser(roles = "USER")
    void student_isDenied() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subAdmin_isDenied() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_canReadDocsAndUi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("WebSchool API"))
                .andExpect(jsonPath("$.components.securitySchemes.jwtCookie.in").value("cookie"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
