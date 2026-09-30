package com.webschool.webschool.user.account.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

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

    // 존재하지 않는 아이디의 가짜 실패 횟수/잠금(보안 점검 L1, 2026-09-30). 예전엔 없는 아이디면 -1을
    // 돌려줘서 "n/5회" 안내가 빠졌는데, 그 차이 자체로 아이디 존재 여부가 드러났다(있는 아이디는
    // ?attempts=1&remaining=4, 없는 아이디는 ?error=true). 없는 아이디도 실제 계정과 똑같이 1~5회 세고
    // 5회면 같은 시간만큼 "잠금"으로 보이게 한다. DB에 없는 값이라 메모리에만 두고 오래된 건 정리한다.
    private record PhantomAttempt(int count, LocalDateTime lockedUntil, LocalDateTime updatedAt) {
    }

    private final ConcurrentHashMap<String, PhantomAttempt> phantomAttempts = new ConcurrentHashMap<>();

    // 반환값: 이번 실패까지 누적된 연속 실패 횟수 - 존재하지 않는 아이디도 위 가짜 카운터로 같은 형태의
    // 값을 돌려준다(호출부 LoginFailureHandler는 계정 존재 여부와 무관하게 똑같이 동작). 보안 로그
    // (/admin/security-log)에는 실제 계정만 남긴다 - AdminActionLog.targetId가 NOT NULL이라 어차피
    // 실제 계정 없이는 기록할 수도 없음.
    @Transactional
    public int recordFailure(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return recordPhantomFailure(username);
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
        LocalDateTime lockedUntil;
        if (user != null) {
            lockedUntil = user.getLockedUntil();
        } else {
            PhantomAttempt phantom = phantomAttempts.get(phantomKey(username));
            lockedUntil = phantom == null ? null : phantom.lockedUntil();
        }
        if (lockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), lockedUntil).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (seconds + 59) / 60;
    }

    // 실제 계정의 recordFailure()와 같은 규칙 - 잠겨 있는 동안엔 더 세지 않고(실제 계정은 이때
    // LockedException으로 비밀번호 검증 전에 걸러진다) 잠금이 풀린 뒤 다시 틀리면 1부터 센다.
    private int recordPhantomFailure(String username) {
        LocalDateTime now = LocalDateTime.now();
        if (phantomAttempts.size() >= PHANTOM_MAX_ENTRIES) {
            purgePhantomAttempts();
        }
        PhantomAttempt updated = phantomAttempts.compute(phantomKey(username), (k, p) -> {
            if (p != null && p.lockedUntil() != null && p.lockedUntil().isAfter(now)) {
                return p;
            }
            int count = (p == null || p.lockedUntil() != null) ? 1 : p.count() + 1;
            LocalDateTime lockedUntil = count >= MAX_ATTEMPTS ? now.plusMinutes(LOCKOUT_MINUTES) : null;
            return new PhantomAttempt(count, lockedUntil, now);
        });
        return updated.count();
    }

    private String phantomKey(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    // 무작위 아이디를 대량으로 넣어 메모리를 채우는 것을 막는 상한 + 주기적 정리. 잠금 시간(5분)보다
    // 충분히 긴 30분 동안 갱신이 없던 항목만 지운다. 로그인 시도 자체도 IP당 속도 제한이 걸려 있다
    // (LoginThrottleFilter).
    private static final int PHANTOM_MAX_ENTRIES = 50_000;

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void purgePhantomAttempts() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        phantomAttempts.entrySet().removeIf(e -> e.getValue().updatedAt().isBefore(cutoff));
    }
}
