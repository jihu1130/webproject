package com.webschool.webschool.global.security.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 캡차(2026-10-12). Cloudflare로 실제 요청을 보내는 부분은 여기서 다루지 않는다 - 키가 없을 때의 동작과
// 응답 해석만 확인한다.
class CaptchaServiceTest {

    // 키가 없는 환경(로컬 개발, 키 발급 전 운영)에서는 캡차가 통째로 꺼져야 한다 - 위젯도 안 나온다.
    @Test
    void withoutKeys_isDisabled() {
        CaptchaService service = new CaptchaService("", "");

        assertFalse(service.isEnabled());
        assertNull(service.siteKeyOrNull());
    }

    // 위젯만 뜨고 서버가 검증을 못 하면 아무도 가입할 수 없다 - 한쪽 키만 있으면 꺼진 것으로 본다.
    @Test
    void withOnlyOneKey_isDisabled() {
        assertFalse(new CaptchaService("site", "").isEnabled());
        assertFalse(new CaptchaService("", "secret").isEnabled());
        assertNull(new CaptchaService("site", " ").siteKeyOrNull());
    }

    @Test
    void withBothKeys_isEnabled() {
        CaptchaService service = new CaptchaService(" site ", "secret");

        assertTrue(service.isEnabled());
        assertEquals("site", service.siteKeyOrNull());
    }

    // 꺼져 있을 때 verify는 통과가 아니라 실패다 - 로그인 대기를 캡차 없이 건너뛸 수 있으면 안 된다.
    @Test
    void verify_whenDisabled_isFalse() {
        assertFalse(new CaptchaService("", "").verify("any-token", "203.0.113.7"));
    }

    // 위젯을 안 풀고 낸 요청(토큰 없음)은 Cloudflare에 묻지 않고 바로 거절한다.
    @Test
    void verify_withoutToken_isFalse() {
        CaptchaService service = new CaptchaService("site", "secret");

        assertFalse(service.verify((String) null, "203.0.113.7"));
        assertFalse(service.verify("  ", "203.0.113.7"));
    }

    @Test
    void successResponse_isReadFromSuccessField() {
        assertTrue(CaptchaService.isSuccessResponse("{\"success\":true,\"hostname\":\"webschool.kro.kr\"}"));
        assertFalse(CaptchaService.isSuccessResponse("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}"));
    }

    // 깨진 응답·빈 응답·success가 없는 응답은 전부 실패로 본다.
    @Test
    void malformedResponse_isFailure() {
        assertFalse(CaptchaService.isSuccessResponse(null));
        assertFalse(CaptchaService.isSuccessResponse(""));
        assertFalse(CaptchaService.isSuccessResponse("<html>502 Bad Gateway</html>"));
        assertFalse(CaptchaService.isSuccessResponse("{\"hostname\":\"webschool.kro.kr\"}"));
        assertFalse(CaptchaService.isSuccessResponse("{\"success\":\"yes\"}"));
    }
}
