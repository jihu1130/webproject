package com.webschool.webschool.user.account.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 보안 점검 L1(2026-09-30) - 존재하지 않는 아이디도 실제 계정과 같은 모양의 실패 횟수/잠금 응답을 받는지.
// 예전엔 없는 아이디면 -1을 돌려줘서 로그인 화면 안내가 달라졌고, 그걸로 아이디 존재 여부가 드러났다.
class LoginAttemptServicePhantomTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LoginAttemptService service =
            new LoginAttemptService(userRepository, mock(AdminActionLogService.class));

    @Test
    void unknownUsernameCountsLikeRealAccountAndLocks() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        for (int i = 1; i <= LoginAttemptService.MAX_ATTEMPTS; i++) {
            assertEquals(i, service.recordFailure("nobody"));
        }
        // 잠긴 동안엔 더 세지 않고 계속 한도 값 + 남은 잠금 시간
        assertEquals(LoginAttemptService.MAX_ATTEMPTS, service.recordFailure("NOBODY"));
        assertTrue(service.getRemainingLockMinutes("nobody") > 0);
    }

    @Test
    void differentUnknownUsernamesAreCountedSeparately() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertEquals(1, service.recordFailure("ghost1"));
        assertEquals(2, service.recordFailure("ghost1"));
        assertEquals(1, service.recordFailure("ghost2"));
    }
}
