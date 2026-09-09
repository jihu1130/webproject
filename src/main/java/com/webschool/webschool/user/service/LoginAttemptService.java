package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
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
    private final AdminActionLogService adminActionLogService;

    // 반환값: 이번 실패까지 누적된 연속 실패 횟수. 존재하지 않는 아이디는 계정 존재 여부를
    // 노출하지 않기 위해 -1을 반환해서 호출부(LoginFailureHandler)가 "n/5회" 안내를 보여주지
    // 않게 한다. 같은 이유로 보안 로그(/admin/security-log)에도 안 남긴다 - AdminActionLog.targetId가
    // NOT NULL이라 어차피 실제 계정 없이는 기록할 수도 없음.
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
        // 보안 로그 - 로그인 시도 자체는 아직 미인증 상태(SecurityContext에 실제 사용자 없음)라
        // AdminActionLogService의 4-arg log()를 쓰면 전부 "system"으로 찍혀서 "누구 계정에 대한
        // 시도였는지" 알 수 없다. 실행자(actor) 자리에 시도 대상 계정명을 직접 넘긴다.
        adminActionLogService.log("USER", user.getId(), "LOGIN_FAIL",
                newAttempts + "/" + MAX_ATTEMPTS + "회 연속 실패", username);
        if (newAttempts >= MAX_ATTEMPTS) {
            userRepository.lockAccountUntil(username, LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            adminActionLogService.log("USER", user.getId(), "ACCOUNT_LOCK",
                    LOCKOUT_MINUTES + "분 잠금", username);
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
