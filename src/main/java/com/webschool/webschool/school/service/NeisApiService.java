package com.webschool.webschool.school.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.webschool.webschool.school.dto.SchoolSearchResultDto;
import com.webschool.webschool.school.dto.TimetableDto;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class NeisApiService {

    @Value("${neis.api.key}")
    private String apiKey;

    // 학교명(키워드)으로 학교 검색 (동명학교 구분을 위한 주소 포함)
    public List<SchoolSearchResultDto> searchSchools(String keyword) {
        List<SchoolSearchResultDto> results = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return results;
        }

        String url = String.format(
                "https://open.neis.go.kr/hub/schoolInfo?KEY=%s&Type=json&pSize=50&SCHUL_NM=%s",
                apiKey, URLEncoder.encode(keyword, StandardCharsets.UTF_8)
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode rows = root.path("schoolInfo").path(1).path("row");

            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String schoolKind = row.path("SCHUL_KND_SC_NM").asText("");
                    // 초등학교는 시간표 조회 API 요청 방식이 달라 아직 지원하지 않음
                    if (schoolKind.contains("초등")) {
                        continue;
                    }

                    results.add(SchoolSearchResultDto.builder()
                            .schoolName(row.path("SCHUL_NM").asText(""))
                            .schoolCode(row.path("SD_SCHUL_CODE").asText(""))
                            .officeCode(row.path("ATPT_OFCDC_SC_CODE").asText(""))
                            .schoolKind(schoolKind)
                            .address(row.path("ORG_RDNMA").asText(""))
                            .build());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    // 학교의 실제 학급(반) 목록 조회 (학년 지정 시 해당 학년만)
    public List<String> fetchClassList(String atptCode, String schoolCode, String grade) {
        java.util.LinkedHashSet<String> distinctClasses = new java.util.LinkedHashSet<>();
        if (atptCode == null || atptCode.isBlank() || schoolCode == null || schoolCode.isBlank()) {
            return new ArrayList<>(distinctClasses);
        }

        int year = java.time.LocalDate.now().getYear();

        StringBuilder urlBuilder = new StringBuilder(String.format(
                "https://open.neis.go.kr/hub/classInfo?KEY=%s&Type=json&pSize=100&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&AY=%d",
                apiKey, atptCode, schoolCode, year
        ));
        if (grade != null && !grade.isBlank()) {
            urlBuilder.append("&GRADE=").append(URLEncoder.encode(grade, StandardCharsets.UTF_8));
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlBuilder.toString())).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode rows = root.path("classInfo").path(1).path("row");

            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String classNm = row.path("CLASS_NM").asText("");
                    if (!classNm.isBlank()) {
                        distinctClasses.add(classNm);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> result = new ArrayList<>(distinctClasses);
        result.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        return result;
    }

    public List<TimetableDto> fetchTimetableFromNeis(String atptCode, String schoolCode, String date, Integer grade, String classNm) {
        List<TimetableDto> timetableList = new ArrayList<>();

        // 💡 핵심 수정: ALL_YMD -> ALL_TI_YMD (나이스 시간표 API 공식 일자 파라미터)
        String url = String.format(
                "https://open.neis.go.kr/hub/misTimetable?KEY=%s&Type=json&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&ALL_TI_YMD=%s&GRADE=%d&CLASS_NM=%s",
                apiKey, atptCode, schoolCode, date, grade, classNm
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            // "row":[ ... ] 안의 데이터만 추출
            if (json.contains("\"row\":[")) {
                int startIdx = json.indexOf("\"row\":[") + 7;
                int endIdx = json.indexOf("]", startIdx);
                if (endIdx > startIdx) {
                    String rowArray = json.substring(startIdx, endIdx);
                    // 개별 객체 단위로 분리
                    String[] items = rowArray.split("\\},\\{");

                    for (String item : items) {
                        String perio = extractValue(item, "PERIO");
                        String subject = extractValue(item, "ITRT_CNTNT");

                        if (!perio.isEmpty() && !subject.isEmpty()) {
                            timetableList.add(TimetableDto.builder()
                                    .perio(perio + "교시")
                                    .subject(subject)
                                    .build());
                        }
                    }
                }
            }

            // 교시 순서 정렬 (1교시 -> 2교시 -> ...)
            timetableList.sort(Comparator.comparingInt(a -> {
                try {
                    return Integer.parseInt(a.getPerio().replace("교시", "").trim());
                } catch (Exception e) {
                    return 0;
                }
            }));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return timetableList;
    }

    // JSON 텍스트 파싱용 헬퍼 메서드
    private String extractValue(String jsonPart, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int start = jsonPart.indexOf(searchKey);
            if (start == -1) return "";
            start += searchKey.length();
            int end = jsonPart.indexOf("\"", start);
            if (end == -1) return "";
            return jsonPart.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    // 급식 정보 조회 메서드 추가
    public String fetchMealFromNeis(String atptCode, String schoolCode, String date) {
        String url = String.format(
                "https://open.neis.go.kr/hub/mealServiceDietInfo?KEY=%s&Type=json&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&MLSV_YMD=%s",
                apiKey, atptCode, schoolCode, date
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            // 급식 데이터가 존재하는지 확인
            if (json.contains("\"mealServiceDietInfo\"") && json.contains("\"DDISH_NM\"")) {
                // DDISH_NM 추출
                int startIndex = json.indexOf("\"DDISH_NM\":\"") + 12;
                int endIndex = json.indexOf("\"", startIndex);
                String rawMenu = json.substring(startIndex, endIndex);

                // <br/> 태그 및 알레르기 유발물질 숫자 (1.2.3. 등) 및 특수문자 제거
                String cleanMenu = rawMenu.replace("\\n", "\n")
                        .replaceAll("<br/>", "\n")
                        .replaceAll("\\([0-9.]+\\)", "")
                        .trim();
                return cleanMenu;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "등록된 급식 정보가 없습니다.";
    }

    // 학사일정 조회 메서드 추가
    public String fetchEventFromNeis(String atptCode, String schoolCode, String date) {
        String url = String.format(
                "https://open.neis.go.kr/hub/SchoolSchedule?KEY=%s&Type=json&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&AA_YMD=%s",
                apiKey, atptCode, schoolCode, date
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            if (json.contains("\"row\":[")) {
                return extractValue(json, "EVENT_NM");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}