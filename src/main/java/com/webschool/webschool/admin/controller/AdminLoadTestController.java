package com.webschool.webschool.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// k6 부하테스트 결과 화면 (사용자 요청, 2026-09-09) - todo.md #24 부하 테스트 항목의 결과를
// "터미널 출력 붙여넣기"가 아니라 관리자 화면에서 바로 보이게 해달라는 요청. k6를 이 앱 안에서
// 직접 실행하는 게 아니라(별도 CLI 도구, 이 프로세스와 무관하게 실행됨), `k6 run
// --summary-export=loadtest/results/파일명.json`으로 남긴 결과 파일들을 읽어서 보여주기만 하는
// 조회 전용 화면이다 - loadtest/README 격인 각 스크립트 상단 주석에 실행법이 있다.
// 서버 상태(AdminDashboardController)와 같은 이유로 총관리자 전용(AdminAccessInterceptor 참고).
@Controller
public class AdminLoadTestController {

    private static final Path RESULTS_DIR = Path.of("loadtest", "results");
    // NeisApiService와 동일한 이유(Jackson 3 tools.jackson.* 네임스페이스, CLAUDE.md 참고)로
    // Spring이 자동 구성하는 ObjectMapper 빈을 주입받지 않고 직접 생성한다.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record RunSummary(
            String fileName,
            String scenarioName,
            LocalDateTime ranAt,
            long totalRequests,
            double requestsPerSec,
            double avgDurationMs,
            double p95DurationMs,
            double errorRatePercent,
            long checksPassed,
            long checksTotal,
            boolean thresholdsPassed) {
    }

    @GetMapping("/admin/loadtest")
    public String loadtest(Model model) throws IOException {
        List<RunSummary> runs = new ArrayList<>();

        if (Files.isDirectory(RESULTS_DIR)) {
            try (Stream<Path> files = Files.list(RESULTS_DIR)) {
                List<Path> jsonFiles = files
                        .filter(p -> p.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(this::lastModifiedSafe).reversed())
                        .toList();
                for (Path path : jsonFiles) {
                    RunSummary summary = parseSummary(path);
                    if (summary != null) {
                        runs.add(summary);
                    }
                }
            }
        }

        model.addAttribute("runs", runs);
        return "admin/loadtest";
    }

    private Instant lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    // k6 run 중간에 이 파일 목록을 읽으면(--summary-export은 테스트가 끝나야 완성된 JSON을 쓴다)
    // 파싱이 실패할 수 있다 - 그 파일 하나만 건너뛰고 나머지는 정상 표시되게 한다.
    private RunSummary parseSummary(Path path) {
        try {
            String content = Files.readString(path);
            JsonNode root = MAPPER.readTree(content);
            JsonNode metrics = root.path("metrics");

            long totalRequests = metrics.path("http_reqs").path("count").asLong(0);
            double requestsPerSec = metrics.path("http_reqs").path("rate").asDouble(0);
            double avg = metrics.path("http_req_duration").path("avg").asDouble(0);
            double p95 = metrics.path("http_req_duration").path("p(95)").asDouble(0);
            double errorRatePercent = metrics.path("http_req_failed").path("value").asDouble(0) * 100;
            long checksPassed = metrics.path("checks").path("passes").asLong(0);
            long checksFailed = metrics.path("checks").path("fails").asLong(0);

            String fileName = path.getFileName().toString();
            return new RunSummary(
                    fileName,
                    deriveScenarioName(fileName),
                    LocalDateTime.ofInstant(lastModifiedSafe(path), ZoneId.systemDefault()),
                    totalRequests,
                    requestsPerSec,
                    avg,
                    p95,
                    errorRatePercent,
                    checksPassed,
                    checksPassed + checksFailed,
                    allThresholdsPassed(metrics));
        } catch (IOException e) {
            return null;
        }
    }

    private String deriveScenarioName(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    // 각 지표(metrics.*)마다 붙는 "thresholds": { "p(95)<200": false } 형태를 전부 훑는다.
    // **직접 확인한 내용(2026-09-09)**: k6 콘솔 출력이 ✓(통과)로 보인 케이스에서 이 값이 false로
    // 나왔다 - 즉 이 boolean은 "통과 여부"가 아니라 "깨졌는지(breached) 여부"다(필드 이름만 보면
    // 반대로 오해하기 쉬움). true가 하나라도 있으면 그 실행은 임계값을 못 지킨 것.
    private boolean allThresholdsPassed(JsonNode metrics) {
        for (Map.Entry<String, JsonNode> metricEntry : metrics.properties()) {
            JsonNode thresholds = metricEntry.getValue().path("thresholds");
            for (Map.Entry<String, JsonNode> thresholdEntry : thresholds.properties()) {
                if (thresholdEntry.getValue().asBoolean(false)) {
                    return false;
                }
            }
        }
        return true;
    }
}
