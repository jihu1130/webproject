package com.webschool.webschool.global.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 보안 점검 M4(2026-09-30) - 요청 횟수 제한기 회귀 테스트.
class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToLimitThenBlocks() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("k", 3, Duration.ofHours(1)));
        }
        assertFalse(limiter.tryAcquire("k", 3, Duration.ofHours(1)));
        assertTrue(limiter.isExhausted("k", 3, Duration.ofHours(1)));
    }

    @Test
    void keysAreIndependent() {
        assertTrue(limiter.tryAcquire("a", 1, Duration.ofHours(1)));
        assertFalse(limiter.tryAcquire("a", 1, Duration.ofHours(1)));
        assertTrue(limiter.tryAcquire("b", 1, Duration.ofHours(1)));
    }

    @Test
    void ipLimitIsSkippedWhenRealIpIsUnknown() {
        // 보안 점검 L3 - 실제 IP를 모르면(모두 127.0.0.1로 보임) 사이트 전체가 같이 막히지 않게 건너뛴다
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquireForIp("p:", "127.0.0.1", 1, Duration.ofHours(1)));
        }
        assertFalse(limiter.isExhaustedForIp("p:", "127.0.0.1", 1, Duration.ofHours(1)));

        assertTrue(limiter.tryAcquireForIp("p:", "1.2.3.4", 1, Duration.ofHours(1)));
        assertFalse(limiter.tryAcquireForIp("p:", "1.2.3.4", 1, Duration.ofHours(1)));
        assertTrue(limiter.isExhaustedForIp("p:", "1.2.3.4", 1, Duration.ofHours(1)));
    }

    @Test
    void windowResetsAfterExpiry() throws InterruptedException {
        assertTrue(limiter.tryAcquire("w", 1, Duration.ofMillis(50)));
        assertFalse(limiter.tryAcquire("w", 1, Duration.ofMillis(50)));
        Thread.sleep(80);
        assertFalse(limiter.isExhausted("w", 1, Duration.ofMillis(50)));
        assertTrue(limiter.tryAcquire("w", 1, Duration.ofMillis(50)));
    }
}
