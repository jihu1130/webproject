package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 로그인 브루트포스 방지(todo.md "고도화 후보" 항목) - 연속 MAX_ATTEMPTS회 비밀번호 실패 시
// LOCKOUT_MINUTES분 동안 계정을 잠근다. User.isLocked()가 lockedUntil을 읽는 시점에 계산하므로
// 여기서 별도의 잠금 해제 처리는 하지 않는다(UserPenaltyService와 동일한 lazy TTL 패턴).
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;

    @Transactional
    public void recordFailure(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return;
        }
        // 이전 잠금이 이미 풀린 상태로 다시 실패한 거라면, 잠기기 전 남아있던 횟수부터 다시
        // 세지 않고 0부터 새로 센다(그대로 두면 만료 직후 한 번만 더 틀려도 곧바로 재잠금된다).
        if (user.getLockedUntil() != null && !user.isLocked()) {
            userRepository.resetFailedLoginAttempts(username);
            user.setFailedLoginAttempts(0);
        }
        userRepository.incrementFailedLoginAttempts(username);
        if (user.getFailedLoginAttempts() + 1 >= MAX_ATTEMPTS) {
            userRepository.lockAccountUntil(username, LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
        }
    }

    @Transactional
    public void recordSuccess(String username) {
        userRepository.resetFailedLoginAttempts(username);
    }
}
