package com.webschool.webschool.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 점검 L4(2026-10-07) - Content-Security-Policy/Referrer-Policy 헤더 회귀 테스트.
// 핵심은 "헤더의 nonce와 화면 인라인 <script>의 nonce가 같다"는 것 - 어긋나면 에러 없이 그 페이지의
// 인라인 스크립트(다크모드 초기화 등)가 전부 조용히 안 돈다.
@SpringBootTest
class SecurityHeadersTest {

    private static final Pattern HEADER_NONCE = Pattern.compile("script-src 'self' 'nonce-([A-Za-z0-9+/=]+)'");
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)([^>]*)>");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginPage_hasCspWithNonceMatchingEveryInlineScript() throws Exception {
        MvcResult result = mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andReturn();

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("object-src 'none'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertFalse(csp.contains("script-src 'self' 'unsafe-inline'"));

        Matcher headerNonce = HEADER_NONCE.matcher(csp);
        assertTrue(headerNonce.find(), csp);
        String nonce = headerNonce.group(1);

        // 로그인 화면엔 navbar 프래그먼트의 인라인 스크립트(다크모드 초기화)가 최소 1개 있다.
        Matcher inline = INLINE_SCRIPT.matcher(result.getResponse().getContentAsString());
        int count = 0;
        while (inline.find()) {
            count++;
            assertTrue(inline.group(1).contains("nonce=\"" + nonce + "\""), "nonce 없는 인라인 스크립트: " + inline.group());
        }
        assertTrue(count > 0);
    }

    @Test
    void nonceChangesOnEveryRequest() throws Exception {
        String first = nonceOf(mvc.perform(get("/login")).andReturn());
        String second = nonceOf(mvc.perform(get("/login")).andReturn());

        assertNotEquals(first, second);
    }

    // 업로드 파일은 UploadResponseHeaderInterceptor의 "sandbox"가 그대로 나가야 한다(더 강한 정책).
    @Test
    void uploads_keepSandboxPolicy() throws Exception {
        MvcResult result = mvc.perform(get("/uploads/editor/none.png")).andReturn();

        assertEquals("sandbox", result.getResponse().getHeader("Content-Security-Policy"));
    }

    private String nonceOf(MvcResult result) {
        Matcher matcher = HEADER_NONCE.matcher(result.getResponse().getHeader("Content-Security-Policy"));
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
