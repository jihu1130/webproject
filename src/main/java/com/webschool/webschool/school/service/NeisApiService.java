package com.webschool.webschool.school.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.webschool.webschool.school.dto.CalendarEventDto;
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
        return fetchTimetableFromNeis(atptCode, schoolCode, date, grade, classNm, null);
    }

    // schoolKind(SCHUL_KND_SC_NM, searchSchools()가 이미 가져오는 값)에 따라 NEIS 시간표
    // 엔드포인트가 달라진다 - 예전엔 misTimetable(중학교 전용)로만 고정돼있어서 고등학교
    // 계열(특성화고/마이스터고 포함 - NEIS는 이들도 SCHUL_KND_SC_NM="고등학교"로 반환하므로
    // 별도 분기 불필요)과 특수학교 학생은 시간표가 아예 안 나왔다.
    public List<TimetableDto> fetchTimetableFromNeis(String atptCode, String schoolCode, String date, Integer grade, String classNm, String schoolKind) {
        List<TimetableDto> timetableList = new ArrayList<>();

        // 💡 핵심 수정: ALL_YMD -> ALL_TI_YMD (나이스 시간표 API 공식 일자 파라미터)
        String url = String.format(
                "https://open.neis.go.kr/hub/%s?KEY=%s&Type=json&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&ALL_TI_YMD=%s&GRADE=%d&CLASS_NM=%s",
                resolveTimetableEndpoint(schoolKind), apiKey, atptCode, schoolCode, date, grade, classNm
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

    // 학교급별 NEIS 시간표 API 엔드포인트 선택. NEIS Open API 문서 기준
    // 고등학교=hisTimetable, 특수학교=spsTimetable, 그 외(중학교 포함, 값이
    // 비어있거나 알 수 없는 경우)는 기존 기본값인 misTimetable을 그대로 유지한다
    // (초등학교는 searchSchools()에서 애초에 검색 결과에서 제외되므로 여기서 다루지 않음).
    private String resolveTimetableEndpoint(String schoolKind) {
        if (schoolKind == null) {
            return "misTimetable";
        }
        if (schoolKind.contains("고등")) {
            return "hisTimetable";
        }
        if (schoolKind.contains("특수")) {
            return "spsTimetable";
        }
        return "misTimetable";
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

    // 급식 정보 조회 - 중식(점심)만 대상으로 한다(SchoolService가 이 결과를 mealType="중식"으로
    // 캐싱하므로 호출부와 반환값의 의미가 일치해야 함).
    public String fetchMealFromNeis(String atptCode, String schoolCode, String date) {
        String url = String.format(
                "https://open.neis.go.kr/hub/mealServiceDietInfo?KEY=%s&Type=json&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&MLSV_YMD=%s",
                apiKey, atptCode, schoolCode, date
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode rows = root.path("mealServiceDietInfo").path(1).path("row");

            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    // 버그 수정: 예전엔 응답에서 첫 번째 DDISH_NM을 끼니 구분 없이 그냥 가져다 썼다 -
                    // 기숙사가 있어 조식/중식/석식을 전부 등록하는 학교는 조식이 "오늘의 급식(중식)"
                    // 으로 잘못 표시됐다. MMEAL_SC_NM으로 중식만 정확히 골라낸다.
                    if (!row.path("MMEAL_SC_NM").asText("").contains("중식")) {
                        continue;
                    }
                    String rawMenu = row.path("DDISH_NM").asText("");
                    if (rawMenu.isBlank()) {
                        continue;
                    }
                    // <br/> 태그만 줄바꿈으로 정리한다. 예전엔 "(5.6.10)"같은 알레르기 유발물질
                    // 코드까지 정규식으로 지웠는데, 이건 알레르기 있는 학생/부모에게 필요한 정보를
                    // 서버가 일부러 버리는 것이었다 - 이제 원본 그대로 보존한다.
                    return rawMenu.replaceAll("<br/>", "\n").trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 버그 수정: 예전엔 여기서 "등록된 급식 정보가 없습니다." 문자열을 반환했는데, 호출부
        // (SchoolService.getCalendarDetails())가 null이 아니고 비어있지도 않다는 이유로 이 문자열
        // 자체를 실제 급식 데이터인 것처럼 DB에 영구 캐싱해버렸다 - 한 번 이렇게 캐싱되면 나중에
        // NEIS에 실제 데이터가 생겨도 영원히 "정보 없음"만 보이는 버그로 이어졌다(아산배방중 등에서
        // 재현). null을 반환해서 실패한 조회가 캐싱되지 않고 다음 방문 때 다시 시도되게 한다 -
        // "정보 없음" 안내 문구는 SchoolService/화면 쪽에서 null일 때만 보여준다.
        return null;
    }

    // 기간 조회 학사일정 - 캘린더 월 그리드에 배지로 보여주기 위한 용도. keyword가
    // 비어있으면(기본값) 그 기간의 학사일정을 전부 반환하고, keyword를 넘기면 이름에
    // 그 키워드가 포함된 것만 걸러서 반환한다(처음엔 "주간"류만 보여주는 걸로
    // 시작했는데, 사용자가 "특수하게 일이 정해진 날 모두 표시해달라"고 넓혀달라고
    // 요청해서 기본은 전체 표시로 바꾸고 필터는 옵션으로만 남김). 새로 추가하는 NEIS
    // 연동이라 신버전(Jackson) 파싱 방식을 따름 - fetchEventFromNeis(구버전, 문자열
    // 자르기)와는 별도 메서드.
    public List<CalendarEventDto> fetchEventsInRange(String atptCode, String schoolCode,
                                                       String fromYmd, String toYmd, String keyword) {
        List<CalendarEventDto> events = new ArrayList<>();
        if (atptCode == null || atptCode.isBlank() || schoolCode == null || schoolCode.isBlank()) {
            return events;
        }

        // pSize를 넉넉하게(1000) 잡음 - 일정명 키워드 필터링은 NEIS 응답을 받아온
        // "뒤에" 자바 쪽에서 하기 때문에(서버가 EVENT_NM 부분일치 검색을 지원하지
        // 않음), 조회 범위가 1년 전체처럼 넓어지면 100건으로는 원하는 일정이
        // 아예 응답에 안 담겨 와서 필터링 이전에 이미 누락될 수 있다.
        String url = String.format(
                "https://open.neis.go.kr/hub/SchoolSchedule?KEY=%s&Type=json&pSize=1000&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&AA_FROM_YMD=%s&AA_TO_YMD=%s",
                apiKey, atptCode, schoolCode, fromYmd, toYmd
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode rows = root.path("SchoolSchedule").path(1).path("row");

            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String eventName = row.path("EVENT_NM").asText("");
                    String ymd = row.path("AA_YMD").asText("");
                    if (eventName.isBlank() || ymd.length() != 8) continue;
                    if (keyword != null && !keyword.isBlank() && !eventName.contains(keyword)) continue;

                    String isoDate = ymd.substring(0, 4) + "-" + ymd.substring(4, 6) + "-" + ymd.substring(6, 8);
                    events.add(CalendarEventDto.builder().date(isoDate).eventName(eventName).build());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return events;
    }

    // 학사일정 조회 메서드 - 특정 날짜에 일정이 여러 개 겹칠 수 있어서(예: "학부모
    // 상담주"이면서 "학교교육과정설명회"인 날) row 전체를 순회해 전부 모은 뒤
    // " · "로 이어붙여 반환한다. 예전엔 EVENT_NM 첫 등장만 문자열 자르기로
    // 가져와서 겹치는 일정 중 하나만 보였던 버그가 있었음 - 새 NEIS 연동 규칙대로
    // Jackson 파싱으로 교체하면서 함께 고침.
    public String fetchEventFromNeis(String atptCode, String schoolCode, String date) {
        String url = String.format(
                "https://open.neis.go.kr/hub/SchoolSchedule?KEY=%s&Type=json&ATPT_OFCDC_SC_CODE=%s&SD_SCHUL_CODE=%s&AA_YMD=%s",
                apiKey, atptCode, schoolCode, date
        );

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode rows = root.path("SchoolSchedule").path(1).path("row");

            if (rows.isArray()) {
                List<String> names = new ArrayList<>();
                for (JsonNode row : rows) {
                    String eventName = row.path("EVENT_NM").asText("");
                    if (!eventName.isBlank() && !names.contains(eventName)) {
                        names.add(eventName);
                    }
                }
                if (!names.isEmpty()) {
                    return String.join(" · ", names);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}