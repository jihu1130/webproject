package com.webschool.webschool.global.security;

import com.webschool.webschool.global.util.ClientIpUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

// 요청 횟수 제한(보안 점검 M4, 2026-09-30) - 비밀번호/아이디 찾기 메일 폭탄, 비로그인 문의 스팸,
// 로그인 대입 시도처럼 "누구나 호출할 수 있고 한 번 호출에 비용이 드는" 동작만 막는다.
// 서버가 EC2 한 대(앱 컨테이너 하나)라 분산 저장소 없이 메모리 고정 윈도우로 충분하다 - 재시작하면
// 카운트가 초기화되는 건 감수한다(잠금이 아니라 속도 제한이라 치명적이지 않음).
//
// 주의: IP를 키로 쓰는 제한은 X-Forwarded-For를 위조하면 우회된다(보안 점검 L3, nginx가 이 헤더를
// 덮어쓰도록 설정해야 완전해짐). 그래서 이메일/아이디처럼 위조가 소용없는 키 제한을 함께 건다.
@Component
public class RateLimiter {

    private record Window(long startMillis, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    // 이번 요청을 세고, 한도 안이면 true. 한도를 넘었으면 세지 않고 false.
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.startMillis() >= window.toMillis()) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (w.count() < limit) {
                allowed[0] = true;
                return new Window(w.startMillis(), w.count() + 1);
            }
            return w;
        });
        return allowed[0];
    }

    // IP를 키로 쓰는 제한 전용. 계산된 클라이언트 IP가 신뢰 프록시 주소(127.0.0.1 등)라면 제한하지 않는다 -
    // nginx가 X-Forwarded-For를 안 보내는 등 실제 IP를 알 수 없는 상황이면 모든 사용자가 같은 IP로 보여서,
    // 그대로 세면 한 사람의 실패/요청 때문에 사이트 전체가 한꺼번에 막힌다(보안 점검 L3). 이메일/계정 기준
    // 제한은 이와 무관하게 계속 걸린다. 로컬 개발(localhost 접속)에서도 같은 이유로 IP 제한이 걸리지 않는다.
    public boolean tryAcquireForIp(String prefix, String ip, int limit, Duration window) {
        if (ClientIpUtils.isTrustedProxy(ip)) {
            return true;
        }
        return tryAcquire(prefix + ip, limit, window);
    }

    public boolean isExhaustedForIp(String prefix, String ip, int limit, Duration window) {
        return !ClientIpUtils.isTrustedProxy(ip) && isExhausted(prefix + ip, limit, window);
    }

    // 세지 않고 이미 한도에 도달했는지만 확인 - 로그인처럼 "실패한 시도만 세고, 막는 건 요청 전에"
    // 하는 경우에 쓴다(LoginThrottleFilter).
    public boolean isExhausted(String key, int limit, Duration window) {
        Window w = windows.get(key);
        return w != null && System.currentTimeMillis() - w.startMillis() < window.toMillis() && w.count() >= limit;
    }

    // 윈도우는 전부 1시간 이하라, 1시간 넘게 갱신 안 된 키는 지워서 메모리가 계속 쌓이지 않게 한다.
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void purgeExpired() {
        long cutoff = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        windows.entrySet().removeIf(e -> e.getValue().startMillis() < cutoff);
    }
}
