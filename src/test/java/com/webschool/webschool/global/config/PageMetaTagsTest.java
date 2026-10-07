package com.webschool.webschool.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 검색 결과·메신저 링크 미리보기용 메타 태그(2026-10-07). 공용 head 프래그먼트 한 곳에서 나가므로, 그 프래그먼트를
// 고치다 빠뜨리면 모든 페이지에서 한꺼번에 사라진다 - 화면에는 아무 변화가 없어 눈으로는 알 수 없다.
@SpringBootTest
class PageMetaTagsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // 페이지가 제목을 따로 정하지 않으면(metaTitle 없음) 사이트 공통 문구가 나가야 한다 - 빈 content가 나가면 안 된다.
    @Test
    void pageWithoutOwnTitle_getsSiteDefaults() throws Exception {
        String html = mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("<html lang=\"ko\""), "html lang");
        assertTrue(html.contains("<meta name=\"description\" content=\"우리 학교"), "description");
        assertTrue(html.contains("<meta property=\"og:title\" content=\"WebSchool - "), "og:title");
        assertTrue(html.contains("<meta property=\"og:description\" content=\"우리 학교"), "og:description");
        assertTrue(html.contains("<meta property=\"og:site_name\" content=\"WebSchool\""), "og:site_name");
    }

    // 스크린 리더와 번역 도구가 언어를 알 수 있게 모든 템플릿의 <html>에 lang이 있어야 한다.
    @Test
    void everyTemplateDeclaresLanguage() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        try (Stream<Path> files = Files.walk(templates)) {
            List<Path> missing = files.filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> {
                        try {
                            return !Files.readString(p, StandardCharsets.UTF_8).contains("<html lang=\"ko\"");
                        } catch (IOException e) {
                            return true;
                        }
                    })
                    .toList();
            assertTrue(missing.isEmpty(), "<html lang=\"ko\">가 없는 템플릿: " + missing);
        }
    }
}
