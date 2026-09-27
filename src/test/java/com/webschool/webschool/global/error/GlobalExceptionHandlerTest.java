package com.webschool.webschool.global.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 전역 예외 처리 회귀 테스트 - 응답 형식(JSON API vs 화면)과 상태 코드 매핑을 확인한다.
// "error" 필드는 프론트 JS가 읽는 기존 계약이라 특히 깨지면 안 된다.
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void jsonHandlerIllegalArgumentBecomes400WithErrorField() throws Exception {
        mockMvc.perform(get("/api/iae"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("제목을 입력해주세요."))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void businessExceptionUsesItsErrorCodeStatus() throws Exception {
        mockMvc.perform(get("/api/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("게시물을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void responseEntityHandlerWithoutResponseBodyIsTreatedAsJson() throws Exception {
        mockMvc.perform(get("/api/entity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("잘못된 값"));
    }

    @Test
    void typeMismatchIs400NotServerError() throws Exception {
        mockMvc.perform(get("/api/number").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void missingParameterIs400() throws Exception {
        mockMvc.perform(get("/api/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void notAcceptableJsonRequestIs406WithoutSecondaryFailure() throws Exception {
        // JSON만 만들 수 있는 핸들러에 text/html만 받겠다는 요청 - 에러 본문을 쓰려다 다시 실패하지 않고 406
        // (String 반환은 text/html로도 쓸 수 있어 406이 안 나므로, 실제 API처럼 객체를 돌려주는 핸들러로 확인)
        mockMvc.perform(get("/api/object").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void unexpectedExceptionIs500WithoutLeakingMessage() throws Exception {
        mockMvc.perform(get("/api/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error").value(ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }

    @Test
    void pageHandlerGoesToErrorPageWithMessageFor4xx() throws Exception {
        mockMvc.perform(get("/page/iae"))
                .andExpect(status().isBadRequest())
                .andExpect(request().attribute(GlobalExceptionHandler.ERROR_MESSAGE_ATTRIBUTE, "잘못된 페이지 요청"));
    }

    @Test
    void pageHandlerServerErrorDoesNotExposeMessage() throws Exception {
        mockMvc.perform(get("/page/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(request().attribute(GlobalExceptionHandler.ERROR_MESSAGE_ATTRIBUTE, (Object) null));
    }

    @Controller
    static class FakeController {

        @GetMapping("/api/iae")
        @ResponseBody
        String iae() {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }

        @GetMapping("/api/not-found")
        @ResponseBody
        String notFound() {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        @GetMapping("/api/entity")
        ResponseEntity<String> entity() {
            throw new IllegalArgumentException("잘못된 값");
        }

        @GetMapping("/api/number")
        @ResponseBody
        String number(@RequestParam int n) {
            return "ok " + n;
        }

        @GetMapping("/api/object")
        @ResponseBody
        Map<String, Object> object() {
            return Map.of("ok", true);
        }

        @GetMapping("/api/boom")
        @ResponseBody
        String boom() {
            throw new IllegalStateException("internal detail that must not leak");
        }

        @GetMapping("/page/iae")
        String pageIae() {
            throw new IllegalArgumentException("잘못된 페이지 요청");
        }

        @GetMapping("/page/boom")
        String pageBoom() {
            throw new IllegalStateException("internal detail");
        }
    }
}
