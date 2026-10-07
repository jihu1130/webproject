package com.webschool.webschool.user.account.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

// 로그인 비밀번호 대입 방지. 2026-10-07에 "계정 잠금"에서 "시도한 쪽만 점점 오래 기다리게 하기"로 바꿨다.
//
// 예전 방식(5회 틀리면 계정을 5분 잠금)의 문제: 잠금이 계정에 걸려서, 아이디만 알면 누구나 남의 계정을
// 계속 잠가둘 수 있었다. 게다가 잠긴 동안에는 이미 로그인해 있던 본인까지 로그아웃됐다
// (JwtAuthenticationFilter가 잠긴 계정의 토큰을 거부). 5분마다 5번씩 틀려주기만 하면 영구히 못 쓰게 된다.
//
// 지금 방식:
// 1. 실패는 "아이디 + 접속한 곳" 단위로 센다. 접속한 곳은 보통 IP, 그 계정으로 로그인에 성공한 적이 있는
//    브라우저(KnownDeviceCookie)는 IP 대신 "아는 기기"라는 별도 몫으로 센다. 그래서 남이 틀린 횟수가 내
//    브라우저의 로그인을 막지 않는다(같은 학교 와이파이 = 같은 IP인 경우에도).
// 2. 5회 연속 실패부터 기다리는 시간이 늘어난다: 30초 -> 5분 -> 30분. 계정은 잠기지 않고, 그 접속한 곳에서의
//    다음 시도만 미뤄진다. 이미 로그인해 있는 사람은 영향이 없다.
// 3. IP를 바꿔 가며 한 계정을 노리는 경우를 위해 계정 전체 상한을 둔다(1시간 50회 실패) - 넘으면 "아는 기기"가
//    아닌 곳의 시도만 그 시간이 끝날 때까지 막는다. 본인의 아는 기기는 계속 로그인할 수 있다.
//
// 남는 한계: 본인이 한 번도 로그인한 적 없는 새 기기에서, 공격자와 같은 IP이거나 3번 상한이 걸린 동안에는
// 기다려야 한다. 이것까지 없애려면 캡차가 필요한데 외부 서비스 키가 있어야 해서 넣지 않았다.
//
// 상태는 전부 메모리에 둔다(서버 1대, 재시작하면 초기화 - RateLimiter와 같은 판단). 없는 아이디도 똑같이
// 세므로 응답만으로는 아이디가 존재하는지 알 수 없다(보안 점검 L1). User.failedLoginAttempts/lockedUntil
// 컬럼은 더 이상 쓰지 않는다(컬럼은 남겨둠 - 지우면 ddl-auto가 처리하지 못한다).
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    public static final int FREE_ATTEMPTS = 5;
    private static final Duration[] WAITS = {Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)};

    static final int ACCOUNT_FAILURE_LIMIT = 50;
    static final Duration ACCOUNT_WINDOW = Duration.ofHours(1);
    private static final String ACCOUNT_KEY_PREFIX = "login-fail-account:";

    // 마지막 실패 후 이만큼 지나면 횟수를 처음부터 다시 센다 - 가끔 한 번씩 틀리는 정상 사용자가 몇 주에 걸쳐
    // 5회를 채워 대기에 걸리지 않게.
    private static final Duration FORGET_AFTER = Duration.ofHours(1);
    private static final int MAX_ENTRIES = 50_000;

    private final UserRepository userRepository;
    private final AdminActionLogService adminActionLogService;
    private final RateLimiter rateLimiter;

    private record Attempts(int count, long blockedUntilMillis, long lastFailureMillis) {
    }

    public record FailureResult(int attempts, long waitSeconds) {
        public boolean blocked() {
            return waitSeconds > 0;
        }
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    // 지금 이 접속한 곳에서 이 아이디로 로그인을 시도해도 되는지 - 기다려야 하면 남은 초, 아니면 0.
    // 비밀번호 검증(BCrypt) 전에 LoginThrottleFilter가 호출한다.
    public long waitSecondsBeforeNextAttempt(String username, String clientIp, boolean knownDevice) {
        long now = System.currentTimeMillis();
        Attempts current = attempts.get(key(username, clientIp, knownDevice));
        long wait = 0;
        if (current != null && current.blockedUntilMillis() > now) {
            wait = toSeconds(current.blockedUntilMillis() - now);
        }
        if (!knownDevice && rateLimiter.isExhausted(accountKey(username), ACCOUNT_FAILURE_LIMIT, ACCOUNT_WINDOW)) {
            // 정확한 남은 시간 대신 창 길이를 안내한다(고정 윈도우의 시작 시각을 밖으로 내보내지 않음).
            wait = Math.max(wait, ACCOUNT_WINDOW.toSeconds());
        }
        return wait;
    }

    @Transactional
    public FailureResult recordFailure(String username, String clientIp, boolean knownDevice) {
        long now = System.currentTimeMillis();
        if (attempts.size() >= MAX_ENTRIES) {
            purge();
        }
        Attempts updated = attempts.compute(key(username, clientIp, knownDevice), (k, a) -> {
            int count = (a == null || now - a.lastFailureMillis() > FORGET_AFTER.toMillis()) ? 1 : a.count() + 1;
            long blockedUntil = count < FREE_ATTEMPTS ? 0 : now + waitFor(count).toMillis();
            return new Attempts(count, blockedUntil, now);
        });
        if (!knownDevice) {
            rateLimiter.tryAcquire(accountKey(username), Integer.MAX_VALUE, ACCOUNT_WINDOW);
        }

        long waitSeconds = updated.blockedUntilMillis() > now ? toSeconds(updated.blockedUntilMillis() - now) : 0;
        // 보안 로그(/admin/security-log)에는 실제 계정만 남긴다 - 로그인 전이라 실행자 자리에 시도 대상
        // 아이디를 직접 넘긴다(4-arg log()는 전부 "system"으로 찍힌다).
        userRepository.findByUsername(username).ifPresent(user -> {
            adminActionLogService.log("USER", user.getId(), "LOGIN_FAIL",
                    updated.count() + "회 연속 실패" + (knownDevice ? " (아는 기기)" : ""), username);
            if (waitSeconds > 0) {
                adminActionLogService.log("USER", user.getId(), "LOGIN_DELAY",
                        "이 접속에서 " + waitSeconds + "초간 시도 제한", username);
            }
        });
        return new FailureResult(updated.count(), waitSeconds);
    }

    // 성공하면 그 접속한 곳의 횟수만 지운다. 계정 전체 상한(3번)은 지우지 않는다 - 공격자가 중간에 자기
    // 계정처럼 성공시켜 초기화할 방법은 없지만, 본인의 성공이 남의 대입 시도 횟수까지 지워줄 이유도 없다.
    public void recordSuccess(String username, String clientIp, boolean knownDevice) {
        attempts.remove(key(username, clientIp, knownDevice));
    }

    static Duration waitFor(int consecutiveFailures) {
        int index = Math.min(consecutiveFailures - FREE_ATTEMPTS, WAITS.length - 1);
        return WAITS[index];
    }

    private String key(String username, String clientIp, boolean knownDevice) {
        return normalize(username) + "|" + (knownDevice ? "known-device" : "ip:" + clientIp);
    }

    private String accountKey(String username) {
        return ACCOUNT_KEY_PREFIX + normalize(username);
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private long toSeconds(long millis) {
        return (millis + 999) / 1000;
    }

    // 무작위 아이디를 대량으로 넣어 메모리를 채우는 것을 막는 상한 + 주기적 정리.
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void purge() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> e.getValue().blockedUntilMillis() < now
                && now - e.getValue().lastFailureMillis() > FORGET_AFTER.toMillis());
    }
}
