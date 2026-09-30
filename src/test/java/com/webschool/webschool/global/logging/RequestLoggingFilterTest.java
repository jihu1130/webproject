package com.webschool.webschool.global.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 요청 로그 필터(2026-09-30) 회귀 테스트 - 요청 ID가 요청 처리 중 MDC에 들어 있고, 응답 헤더로 돌아가고,
// 요청이 끝나면 MDC가 비워지는지(스레드 풀 재사용 시 다른 요청에 남은 ID/사용자가 섞이면 안 됨),
// 앞단이 보낸 ID는 안전한 형식일 때만 이어 쓰는지(개행 등으로 로그 위조 방지).
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void requestId_isInMdcDuringRequest_setOnResponse_andClearedAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenDuringRequest.set(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID));
                MDC.put(RequestLoggingFilter.MDC_USER, "test1");
            }
        });

        String header = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertEquals(header, seenDuringRequest.get());
        assertEquals(12, header.length());
        assertNull(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID));
        assertNull(MDC.get(RequestLoggingFilter.MDC_USER));
    }

    @Test
    void safeIncomingRequestId_isReused() {
        assertEquals("abc-123", RequestLoggingFilter.resolveRequestId("abc-123"));
    }

    @Test
    void unsafeIncomingRequestId_isReplaced() {
        String forged = "abc\nFAKE LOG LINE";
        String resolved = RequestLoggingFilter.resolveRequestId(forged);
        assertNotEquals(forged, resolved);
        assertFalse(resolved.contains("\n"));
        assertNotEquals("x".repeat(65), RequestLoggingFilter.resolveRequestId("x".repeat(65)));
    }

    @Test
    void staticAndMonitoringPaths_areSkipped() {
        assertTrue(RequestLoggingFilter.isSkipped("/css/post.css"));
        assertTrue(RequestLoggingFilter.isSkipped("/actuator/health"));
        assertTrue(RequestLoggingFilter.isSkipped("/v3/api-docs"));
        assertFalse(RequestLoggingFilter.isSkipped("/posts"));
        assertFalse(RequestLoggingFilter.isSkipped("/notifications/unread-count"));
    }
}
