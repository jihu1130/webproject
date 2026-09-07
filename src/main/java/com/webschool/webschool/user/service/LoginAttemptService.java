package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

// 로그인 브루트포스 방지(todo.md "고도화 후보" 항목) - 연속 MAX_ATTEMPTS회 비밀번호 실패 시
// LOCKOUT_MINUTES분 동안 계정을 잠근다. User.isLocked()가 lockedUntil을 읽는 시점에 계산하므로
// 여기서 별도의 잠금 해제 처리는 하지 않는다(UserPenaltyService와 동일한 lazy TTL 패턴).
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 5;

    private final UserRepository userRepository;

    // 반환값: 이번 실패까지 누적된 연속 실패 횟수. 존재하지 않는 아이디는 계정 존재 여부를
    // 노출하지 않기 위해 -1을 반환해서 호출부(LoginFailureHandler)가 "n/5회" 안내를 보여주지
    // 않게 한다.
    @Transactional
    public int recordFailure(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return -1;
        }
        // 이전 잠금이 이미 풀린 상태로 다시 실패한 거라면, 잠기기 전 남아있던 횟수부터 다시
        // 세지 않고 0부터 새로 센다(그대로 두면 만료 직후 한 번만 더 틀려도 곧바로 재잠금된다).
        if (user.getLockedUntil() != null && !user.isLocked()) {
            userRepository.resetFailedLoginAttempts(username);
            user.setFailedLoginAttempts(0);
        }
        userRepository.incrementFailedLoginAttempts(username);
        int newAttempts = user.getFailedLoginAttempts() + 1;
        if (newAttempts >= MAX_ATTEMPTS) {
            userRepository.lockAccountUntil(username, LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
        }
        return newAttempts;
    }

    @Transactional
    public void recordSuccess(String username) {
        userRepository.resetFailedLoginAttempts(username);
    }

    // 잠금 화면에 "N분 후 다시 시도해주세요"를 정확히 보여주기 위한 남은 시간 계산 - 초 단위
    // 나머지가 있으면 올림 처리해서(예: 4분 10초 남음 -> "5분") 실제 해제 시각보다 이르게
    // "곧 풀린다"고 보여주지 않는다. 잠긴 상태가 아니면(계정 없음/이미 풀림) 0을 반환.
    public long getRemainingLockMinutes(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getLockedUntil() == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), user.getLockedUntil()).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (seconds + 59) / 60;
    }
}
