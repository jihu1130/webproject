package com.webschool.webschool.home.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// robots.txt는 로그인 없이 열려야 한다 - SecurityConfig의 permitAll에서 빠지면 검색 로봇이 /login으로
// 튕겨서 로그인 화면 HTML을 robots.txt로 읽게 된다(화면에는 아무 변화가 없어 눈으로는 알 수 없다).
@SpringBootTest
class RobotsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousCanReadRobotsTxt() throws Exception {
        var response = mvc.perform(get("/robots.txt")).andExpect(status().isOk()).andReturn().getResponse();
        String body = response.getContentAsString();

        assertTrue(response.getContentType().startsWith("text/plain"), "content type: " + response.getContentType());
        assertTrue(body.startsWith("User-agent: *\n"), body);
        assertTrue(body.contains("Disallow: /admin\n"), body);
        assertTrue(body.contains("Disallow: /mypage\n"), body);
        // 공개 화면까지 막으면 검색에 아예 안 나온다
        assertFalse(body.contains("Disallow: /\n"), body);
        assertFalse(body.contains("Disallow: /posts"), body);
    }
}
