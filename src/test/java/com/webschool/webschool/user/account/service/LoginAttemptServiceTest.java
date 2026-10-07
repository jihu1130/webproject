package com.webschool.webschool.user.account.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 로그인 대입 방지 회귀 테스트(2026-10-07). 핵심은 "남이 내 아이디로 비밀번호를 틀려도 나는 로그인할 수 있다" -
// 예전 방식(계정 5분 잠금)은 아이디만 알면 누구나 남의 계정을 계속 잠가둘 수 있었다.
class LoginAttemptServiceTest {

    private static final String ATTACKER_IP = "203.0.113.50";
    private static final String VICTIM_IP = "198.51.100.20";

    private final UserRepository userRepository = mock(UserRepository.class);
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(userRepository, mock(AdminActionLogService.class), new RateLimiter());
        User victim = new User();
        victim.setId(1L);
        victim.setUsername("victim");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("victim")).thenReturn(Optional.of(victim));
    }

    private void fail(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username, ip, false);
        }
    }

    // ---- 계정 잠금 DoS가 더 이상 안 된다 ----

    @Test
    void attackersFailures_doNotBlockVictimOnAnotherNetwork() {
        fail("victim", ATTACKER_IP, 10);

        assertTrue(service.waitSecondsBeforeNextAttempt("victim", ATTACKER_IP, false) > 0);
        assertEquals(0, service.waitSecondsBeforeNextAttempt("victim", VICTIM_IP, false));
    }

    // 같은 학교 와이파이(같은 공인 IP)에서 공격해도, 그 계정으로 로그인한 적 있는 브라우저는 따로 센다.
    @Test
    void attackersFailuresOnSameNetwork_doNotBlockVictimsKnownDevice() {
        fail("victim", ATTACKER_IP, 10);

        assertTrue(service.waitSecondsBeforeNextAttempt("victim", ATTACKER_IP, false) > 0);
        assertEquals(0, service.waitSecondsBeforeNextAttempt("victim", ATTACKER_IP, true));
    }

    // IP를 바꿔 가며 계정 전체 상한(1시간 50회)을 넘겨도 아는 기기는 계속 로그인할 수 있다.
    @Test
    void accountWideCeiling_blocksUnknownClientsOnly() {
        for (int i = 0; i < LoginAttemptService.ACCOUNT_FAILURE_LIMIT; i++) {
            service.recordFailure("victim", "203.0.113." + i, false);
        }

        // 한 번도 실패한 적 없는 새 IP도 막힌다(분산 대입 방지)...
        assertTrue(service.waitSecondsBeforeNextAttempt("victim", "192.0.2.99", false) > 0);
        // ...본인의 아는 기기는 통과.
        assertEquals(0, service.waitSecondsBeforeNextAttempt("victim", "192.0.2.99", true));
        // 다른 계정에는 영향 없음.
        assertEquals(0, service.waitSecondsBeforeNextAttempt("someoneelse", "192.0.2.99", false));
    }

    // ---- 그래도 대입은 느려진다 ----

    @Test
    void firstFourFailures_areFree_thenWaitGrows() {
        for (int i = 1; i <= 4; i++) {
            LoginAttemptService.FailureResult result = service.recordFailure("victim", ATTACKER_IP, false);
            assertEquals(i, result.attempts());
            assertFalse(result.blocked());
        }

        assertEquals(30, service.recordFailure("victim", ATTACKER_IP, false).waitSeconds());
        assertEquals(Duration.ofSeconds(30), LoginAttemptService.waitFor(5));
        assertEquals(Duration.ofMinutes(5), LoginAttemptService.waitFor(6));
        assertEquals(Duration.ofMinutes(30), LoginAttemptService.waitFor(7));
        assertEquals(Duration.ofMinutes(30), LoginAttemptService.waitFor(40));
    }

    // 아는 기기도 무제한은 아니다 - 기기 쿠키를 훔쳐도 그 몫의 5회 뒤에는 똑같이 기다려야 한다.
    @Test
    void knownDevice_hasItsOwnLimit() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("victim", VICTIM_IP, true);
        }

        assertTrue(service.waitSecondsBeforeNextAttempt("victim", VICTIM_IP, true) > 0);
    }

    @Test
    void success_clearsOnlyThatClientsCount() {
        fail("victim", ATTACKER_IP, 5);
        fail("victim", VICTIM_IP, 3);

        service.recordSuccess("victim", VICTIM_IP, false);

        assertEquals(1, service.recordFailure("victim", VICTIM_IP, false).attempts());
        assertTrue(service.waitSecondsBeforeNextAttempt("victim", ATTACKER_IP, false) > 0);
    }

    // ---- 아이디 존재 여부가 드러나지 않는다(보안 점검 L1) ----

    @Test
    void unknownUsername_behavesExactlyLikeRealAccount() {
        for (int i = 1; i <= 4; i++) {
            assertEquals(service.recordFailure("victim", ATTACKER_IP, false),
                    service.recordFailure("nobody", ATTACKER_IP, false));
        }
        assertEquals(service.recordFailure("victim", ATTACKER_IP, false).waitSeconds(),
                service.recordFailure("nobody", ATTACKER_IP, false).waitSeconds());
        assertTrue(service.waitSecondsBeforeNextAttempt("NOBODY", ATTACKER_IP, false) > 0);
    }

    @Test
    void usernamesAreCountedSeparatelyAndCaseInsensitively() {
        assertEquals(1, service.recordFailure("ghost1", ATTACKER_IP, false).attempts());
        assertEquals(2, service.recordFailure("GHOST1", ATTACKER_IP, false).attempts());
        assertEquals(1, service.recordFailure("ghost2", ATTACKER_IP, false).attempts());
    }
}
