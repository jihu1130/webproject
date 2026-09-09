package com.webschool.webschool.admin.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// 관리자 대시보드 "서버 상태" 카드/그래프(AdminDashboardController, 사용자 요청 2026-09-09)의
// 데이터 소스. 현재 값은 Actuator/Micrometer 게이지에서 매 요청마다 바로 읽지만(getSnapshot()),
// 그래프용 추이는 게이지가 "현재 값"만 주고 과거값을 기억하지 않으므로 이 서비스가 1분마다 직접
// 샘플을 찍어 메모리에 쌓아둔다(PostContestService의 기존 @Scheduled 패턴과 동일) - 서버
// 재시작하면 history는 비워짐, DB에 영속화하지 않음(장기 이력이 필요하면 이미 있는 로컬
// Prometheus/Grafana 스택(#24)을 쓰는 게 맞고, 이건 그 스택이 없어도 대시보드 화면 안에서
// 바로 보이는 "최근 1시간" 용도로만 충분하면 됨).
@Service
@RequiredArgsConstructor
public class ServerMetricsHistoryService {

    private static final int MAX_SAMPLES = 60; // 1분 간격 샘플링 * 60 = 최근 1시간

    private final MeterRegistry meterRegistry;
    private final SessionRegistry sessionRegistry;

    private final Deque<Sample> history = new ArrayDeque<>();

    public record Sample(LocalDateTime time, Double cpuPercent, long heapUsedMb, long activeSessionCount) {
    }

    public record Snapshot(long activeSessionCount, Double cpuUsagePercent, long heapUsedMb, long heapCommittedMb,
                            long heapUsedPercent, Double diskFreeGb, Double diskTotalGb, String uptimeLabel) {
    }

    @Scheduled(initialDelay = 0, fixedRate = 60_000)
    public void sample() {
        Sample s = new Sample(
                LocalDateTime.now(),
                roundPercent(gaugeValue("process.cpu.usage")),
                toMb(sumGauges("jvm.memory.used", "area", "heap")),
                countActiveSessions());
        synchronized (history) {
            history.addLast(s);
            while (history.size() > MAX_SAMPLES) {
                history.removeFirst();
            }
        }
    }

    public List<Sample> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public Snapshot getSnapshot() {
        double heapUsedBytes = sumGauges("jvm.memory.used", "area", "heap");
        double heapCommittedBytes = sumGauges("jvm.memory.committed", "area", "heap");
        return new Snapshot(
                countActiveSessions(),
                roundPercent(gaugeValue("process.cpu.usage")),
                toMb(heapUsedBytes),
                toMb(heapCommittedBytes),
                heapCommittedBytes > 0 ? Math.round(heapUsedBytes / heapCommittedBytes * 100) : 0,
                toGb(firstGaugeValue("disk.free")),
                toGb(firstGaugeValue("disk.total")),
                formatUptime(gaugeValue("process.uptime")));
    }

    // 로그인 중인 계정 수가 아니라 "동시 세션 수"(같은 계정으로 여러 브라우저에서 로그인하면
    // 그만큼 더 잡힘) - SecurityConfig의 sessionRegistry()가 세션 생성/소멸
    // (HttpSessionEventPublisher 경유)을 계속 추적하므로 만료된 세션은 자동으로 빠진다
    // (expiredOnly=false로 살아있는 것만 조회).
    private long countActiveSessions() {
        return sessionRegistry.getAllPrincipals().stream()
                .mapToLong(principal -> sessionRegistry.getAllSessions(principal, false).size())
                .sum();
    }

    // MeterRegistry.find()는(=RequiredSearch가 아니라 Search) 지표가 없어도 예외 없이 null/빈
    // 컬렉션을 준다 - 플랫폼에 따라 없을 수 있는 지표(예: system.load.average.1m은 Windows에
    // 없음)를 다룰 때 이 쪽이 안전하다.
    private Double gaugeValue(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        return gauge == null ? null : gauge.value();
    }

    private Double firstGaugeValue(String name) {
        return meterRegistry.find(name).gauges().stream().findFirst().map(Gauge::value).orElse(null);
    }

    // jvm.memory.* 는 힙 메모리 풀(Eden/Survivor/Old 등)마다 별도 게이지로 쪼개져 있어서,
    // "힙 전체 사용량" 하나로 보려면 area=heap 태그를 가진 것들을 다 더해야 한다.
    private double sumGauges(String name, String tagKey, String tagValue) {
        return meterRegistry.find(name).tags(tagKey, tagValue).gauges().stream()
                .mapToDouble(Gauge::value)
                .sum();
    }

    private long toMb(double bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    private Double toGb(Double bytes) {
        return bytes == null ? null : bytes / (1024.0 * 1024.0 * 1024.0);
    }

    private Double roundPercent(Double ratio) {
        // process.cpu.usage는 0.0~1.0 비율 - JVM이 갓 시작해 표본이 아직 없으면 -1을 주기도 한다.
        if (ratio == null || ratio < 0) return null;
        return Math.round(ratio * 1000) / 10.0;
    }

    private String formatUptime(Double uptimeSeconds) {
        if (uptimeSeconds == null) return "-";
        Duration duration = Duration.ofSeconds(uptimeSeconds.longValue());
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? hours + "시간 " + minutes + "분" : minutes + "분";
    }
}
