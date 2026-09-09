package com.webschool.webschool.weather.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// 학교 주소(도로명주소 문자열)를 기상청 단기예보 격자좌표(nx,ny)로 변환한다. 격자 매핑 자료
// (data.kma.go.kr 제공, 시도/시군구/읍면동 단위 ~3800행)가 src/main/resources/data/kma-grid.csv에
// 있어야 실제로 동작하고, 이 파일이 없으면 날씨 기능 전체가 예외 없이 조용히 비활성 상태로
// 남는다(코드는 정상 컴파일/기동됨) - CLAUDE.md의 "외부 API 실패 시 null/빈값 반환, throw 안 함"
// 원칙과 동일선상.
@Service
public class WeatherGridResolver {

    private static final Logger log = LoggerFactory.getLogger(WeatherGridResolver.class);
    private static final String CSV_PATH = "data/kma-grid.csv";

    private final List<GridEntry> entries = new ArrayList<>();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        if (!resource.exists()) {
            log.warn("{}가 없어 날씨 격자 매칭이 비활성화됩니다 - 기상청 법정동-격자 매핑 자료를 준비해서 배치할 것", CSV_PATH);
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (firstLine) {
                    firstLine = false;
                    if (!Character.isDigit(line.charAt(line.length() - 1)) && line.contains(",")) {
                        continue; // 헤더 행(숫자로 안 끝남)은 건너뜀
                    }
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 5) {
                    continue;
                }
                String sido = parts[0].trim();
                String sigungu = parts[1].trim();
                String dong = parts[2].trim();
                try {
                    int nx = Integer.parseInt(parts[3].trim());
                    int ny = Integer.parseInt(parts[4].trim());
                    entries.add(new GridEntry(sido, sigungu, dong, nx, ny));
                } catch (NumberFormatException e) {
                    // 파싱 안 되는 행은 건너뜀(헤더/공백 등)
                }
            }
        } catch (IOException e) {
            log.warn("kma-grid.csv 로드 실패: {}", e.toString());
            return;
        }

        // 더 구체적인(문자열 총합이 긴 = 읍면동까지 명시된) 항목을 먼저 검사해야 "서울특별시"
        // 같은 광역 단위 오매칭보다 정확한 매칭이 우선된다.
        entries.sort((a, b) -> b.matchLength() - a.matchLength());
        log.info("kma-grid.csv 로드 완료 - {}행", entries.size());
    }

    // 매칭 실패 시 null 반환(예외 없음) - 호출부(WeatherService)가 그 학교만 날씨 기능을
    // 조용히 건너뛰게 한다.
    public int[] resolve(String address) {
        if (address == null || address.isBlank() || entries.isEmpty()) {
            return null;
        }
        for (GridEntry entry : entries) {
            if (address.contains(entry.sido)
                    && (entry.sigungu.isBlank() || address.contains(entry.sigungu))
                    && (entry.dong.isBlank() || address.contains(entry.dong))) {
                return new int[]{entry.nx, entry.ny};
            }
        }
        return null;
    }

    private record GridEntry(String sido, String sigungu, String dong, int nx, int ny) {
        int matchLength() {
            return sido.length() + sigungu.length() + dong.length();
        }
    }
}
