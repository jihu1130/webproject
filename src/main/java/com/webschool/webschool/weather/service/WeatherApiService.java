package com.webschool.webschool.weather.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

// 기상청 단기예보(동네예보) getVilageFcst 연동. NeisApiService와 동일한 패턴(공유 정적
// HttpClient, @Value 주입 키, 재시도+백오프, 실패 시 null/빈 결과 반환 - 절대 throw 안 함,
// 로그에 키 마스킹, Jackson JsonNode 파싱).
@Service
public class WeatherApiService {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 400;

    // 기상청 단기예보 발표 시각(하루 8회) - 발표 후 API에 실제로 반영되기까지 완충 시간을 둔다.
    private static final int[] ANNOUNCE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int ANNOUNCE_DELAY_MINUTES = 10;
    // base_date/base_time을 최대 이만큼 과거로 되돌려가며 재시도(발표 직후 반영 지연 대비).
    private static final int MAX_BASE_TIME_FALLBACKS = 3;

    // 기본값을 빈 문자열로 둔다(NeisApiService.apiKey와 다른 점) - neis.api.key는 이 프로젝트를
    // 받으면 바로 채워야 하는 필수 값이라 기본값이 없어도 되지만, weather.api.key는 이 기능이
    // 나중에 추가되면서 기존 application.yml(로컬/운영, git 비추적)엔 아직 없는 값이다. 기본값이
    // 없으면 그 키가 채워지기 전까지 앱 자체가 기동 실패한다(BeanCreationException) - 실제로
    // 겪음. 빈 값이면 send()가 자연히 인증 실패로 null을 반환해 날씨 기능만 조용히 비활성화된다.
    @Value("${weather.api.key:}")
    private String apiKey;

    // nx, ny 격자의 현재 시점 최신 예보를 가져와 날짜별로 묶어 반환한다. 응답에 담긴 fcstDate
    // 범위(발표 시점 기준 대략 D+2~D+3)만큼만 채워지고, 그보다 먼 미래 날짜는 결과 Map에
    // 아예 없다 - 호출부(WeatherService)가 이미 캐시된 날짜는 재조회하지 않으므로, 이 메서드는
    // 매번 "지금 시점에 알 수 있는 만큼"만 반환하면 된다.
    public Map<LocalDate, RawDayForecast> fetchForecast(int nx, int ny) {
        LocalDateTime now = LocalDateTime.now();
        for (int fallback = 0; fallback <= MAX_BASE_TIME_FALLBACKS; fallback++) {
            String[] baseDateTime = resolveBaseDateTime(now, fallback);
            Map<LocalDate, RawDayForecast> result = fetchOnce(nx, ny, baseDateTime[0], baseDateTime[1]);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return Map.of();
    }

    private Map<LocalDate, RawDayForecast> fetchOnce(int nx, int ny, String baseDate, String baseTime) {
        String url = String.format(
                "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                        + "?serviceKey=%s&pageNo=1&numOfRows=1000&dataType=JSON"
                        + "&base_date=%s&base_time=%s&nx=%d&ny=%d",
                apiKey, baseDate, baseTime, nx, ny
        );

        String body = send(url);
        if (body == null) {
            return null;
        }

        Map<LocalDate, RawDayForecast> byDate = new HashMap<>();
        try {
            JsonNode root = MAPPER.readTree(body);
            String resultCode = root.path("response").path("header").path("resultCode").asText("");
            if (!"00".equals(resultCode)) {
                log.warn("기상청 API가 비정상 결과코드를 반환했습니다 (resultCode={}, base={}/{})",
                        resultCode, baseDate, baseTime);
                return null;
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");
            if (!items.isArray()) {
                return byDate;
            }

            for (JsonNode item : items) {
                String category = item.path("category").asText("");
                String fcstDateStr = item.path("fcstDate").asText("");
                String fcstTimeStr = item.path("fcstTime").asText("");
                String value = item.path("fcstValue").asText("");
                if (fcstDateStr.length() != 8 || value.isBlank()) {
                    continue;
                }
                LocalDate fcstDate = LocalDate.parse(fcstDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                RawDayForecast day = byDate.computeIfAbsent(fcstDate,
                        d -> new RawDayForecast(d, baseDate, baseTime));
                applyCategory(day, category, value, fcstTimeStr);
            }
        } catch (Exception e) {
            log.warn("기상청 API 응답 파싱 실패 (nx={}, ny={}, base={}/{}): {}", nx, ny, baseDate, baseTime, e.toString());
            return null;
        }
        return byDate;
    }

    private void applyCategory(RawDayForecast day, String category, String value, String fcstTime) {
        try {
            switch (category) {
                case "POP" -> day.pop = Math.max(day.pop == null ? 0 : day.pop, Integer.parseInt(value));
                case "PTY" -> {
                    int code = Integer.parseInt(value);
                    // 코드가 클수록 더 심한 강수형태로 취급(0=없음이 항상 가장 낮음) - 하루 중
                    // 가장 심한 상태를 그날의 대표값으로 삼는다.
                    if (day.pty == null || code > Integer.parseInt(day.pty)) {
                        day.pty = String.valueOf(code);
                    }
                }
                case "SKY" -> {
                    // 정오(1200) 발표값을 그날의 대표 하늘상태로 우선 채택, 없으면 처음 들어온 값 유지.
                    if ("1200".equals(fcstTime) || day.skyCode == null) {
                        day.skyCode = value;
                    }
                }
                case "TMX" -> day.tmx = (int) Double.parseDouble(value);
                case "TMN" -> day.tmn = (int) Double.parseDouble(value);
                default -> { /* 그 외 카테고리(TMP, REH, WSD 등)는 이 위젯에서 쓰지 않음 */ }
            }
        } catch (NumberFormatException ignored) {
            // 예보 값이 숫자가 아니면(드묾) 그 카테고리만 건너뜀
        }
    }

    // 지금 시점에서 가장 최근 발표 시각을 계산한다. fallback을 늘리면 그보다 한 단계씩 더
    // 과거 발표분으로 되돌아간다(가장 최근 발표분이 아직 API에 반영 안 됐을 때 대비).
    private String[] resolveBaseDateTime(LocalDateTime now, int fallback) {
        LocalDateTime cursor = now.minusMinutes(ANNOUNCE_DELAY_MINUTES);
        for (int step = 0; step <= fallback; step++) {
            cursor = latestAnnounceBefore(cursor);
            if (step < fallback) {
                cursor = cursor.minusMinutes(1); // 다음 fallback 탐색을 위해 한 발표 슬롯 이전으로
            }
        }
        return new String[]{
                cursor.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                cursor.toLocalTime().format(DateTimeFormatter.ofPattern("HHmm"))
        };
    }

    private LocalDateTime latestAnnounceBefore(LocalDateTime reference) {
        LocalDate date = reference.toLocalDate();
        LocalTime time = reference.toLocalTime();
        for (int i = ANNOUNCE_HOURS.length - 1; i >= 0; i--) {
            if (!time.isBefore(LocalTime.of(ANNOUNCE_HOURS[i], 0))) {
                return LocalDateTime.of(date, LocalTime.of(ANNOUNCE_HOURS[i], 0));
            }
        }
        // 오늘 첫 발표(02시)보다 이른 시각이면 전날 마지막 발표(23시)를 쓴다.
        return LocalDateTime.of(date.minusDays(1), LocalTime.of(ANNOUNCE_HOURS[ANNOUNCE_HOURS.length - 1], 0));
    }

    private String send(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn("기상청 API가 비정상 응답을 반환했습니다 (status={}, url={})",
                            response.statusCode(), maskApiKey(url));
                    return null;
                }
                return response.body();
            } catch (IOException | InterruptedException e) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                boolean lastAttempt = attempt == MAX_ATTEMPTS;
                log.warn("기상청 API 호출 실패 ({}/{}번째 시도{}, url={}): {}",
                        attempt, MAX_ATTEMPTS, lastAttempt ? " - 포기" : " - 재시도",
                        maskApiKey(url), e.toString());
                if (lastAttempt) {
                    return null;
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private String maskApiKey(String url) {
        return url.replaceAll("serviceKey=[^&]*", "serviceKey=***");
    }

    // WeatherApiService 응답 파싱 결과를 WeatherService가 WeatherForecastCache로 옮겨 담기 전까지
    // 담는 임시 그릇 - 엔티티를 직접 여기서 만들지 않는 이유는 fetchedAt/nx/ny 같은 캐시 저장
    // 책임은 WeatherService가 가져야 하기 때문(이 클래스는 순수 API 클라이언트로만 남김).
    public static class RawDayForecast {
        public final LocalDate date;
        public final String baseDate;
        public final String baseTime;
        public Integer pop;
        public String pty;
        public String skyCode;
        public Integer tmx;
        public Integer tmn;

        public RawDayForecast(LocalDate date, String baseDate, String baseTime) {
            this.date = date;
            this.baseDate = baseDate;
            this.baseTime = baseTime;
        }
    }
}
