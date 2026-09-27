package com.webschool.webschool.school.controller;

import com.webschool.webschool.school.dto.CalendarEventDto;
import com.webschool.webschool.school.dto.SchoolCalendarDto;
import com.webschool.webschool.school.dto.SchoolSearchResultDto;
import com.webschool.webschool.school.dto.TimetableDto;
import com.webschool.webschool.school.dto.VacationDdayDto;
import com.webschool.webschool.school.service.NeisApiService;
import com.webschool.webschool.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// 캘린더 페이지와 NEIS 기반 조회 API(학교 검색/반 목록/시간표/급식/학사일정/방학 D-Day).
// 2026-09-28 파일 정리 때 "오늘의 한마디"(/school/comments/**, /school/api/comments/**)는
// ScheduleCommentController로, 개인 일정(/school/api/personal-events/**)은
// PersonalEventController로 분리했다 - 세 컨트롤러 모두 같은 /school 접두사를 쓴다.
@Controller
@RequestMapping("/school")
@RequiredArgsConstructor
public class SchoolController {

    private final NeisApiService neisApiService;
    private final SchoolService schoolService;

    // 1. 캘린더 페이지 요청 (/school/calendar)
    // http://localhost:8888/school/calendar
    @GetMapping("/calendar")
    public String calendarPage() {
        return "school/calendar"; // templates/school/calendar.html 렌더링
    }

    // 2. 시간표 JSON API 요청 (/school/api/timetable)
    @GetMapping("/api/timetable")
    @ResponseBody
    public List<TimetableDto> getTimetableApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") Integer grade,
            @RequestParam(defaultValue = "1") String classNm,
            @RequestParam(required = false) String schoolKind) {

        try {
            return neisApiService.fetchTimetableFromNeis(atptCode, schoolCode, date, grade, classNm, schoolKind);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 3. 학교명(키워드) 검색 API (동명학교 구분을 위해 주소를 함께 반환)
    @GetMapping("/api/search")
    @ResponseBody
    public List<SchoolSearchResultDto> searchSchools(@RequestParam String keyword) {
        return neisApiService.searchSchools(keyword);
    }

    // 3-1. 선택한 학교(+학년)의 실제 반 목록 조회
    @GetMapping("/api/classes")
    @ResponseBody
    public List<String> getClasses(@RequestParam String atptCode,
                                    @RequestParam String schoolCode,
                                    @RequestParam(required = false) String grade) {
        return neisApiService.fetchClassList(atptCode, schoolCode, grade);
    }

    @GetMapping("/api/calendar-details")
    @ResponseBody
    public SchoolCalendarDto getCalendarDetailsApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") Integer grade,
            @RequestParam(defaultValue = "1") String classNm,
            @RequestParam(required = false) String schoolKind) {

        try {
            return schoolService.getCalendarDetails(atptCode, schoolCode, date, grade, classNm, schoolKind);
        } catch (DataIntegrityViolationException e) {
            // 같은 학교/학년/반/날짜 시간표 캐시가 비어있을 때 동시에 여러 요청이 몰리면
            // 전부 캐시 미스로 보고 나이스 조회 결과를 DB에 저장하려다 unique 제약(학교+학년+반+
            // 날짜+교시)에서 충돌한다(todo.md #24 "캘린더 NEIS 캐시 만료 몰림" 참고, 운영에서
            // 실제로 반복 발생 확인됨). 이 요청은 진 쪽이므로 한 번 더 시도하면 이번엔 이긴 쪽이
            // 이미 커밋해둔 캐시를 그대로 읽어온다 - InnoDB 행 잠금 때문에 진 쪽이
            // DataIntegrityViolationException을 받는 시점엔 이긴 쪽이 이미 커밋을 마친 상태라
            // 재시도가 즉시 캐시 히트로 이어진다.
            return schoolService.getCalendarDetails(atptCode, schoolCode, date, grade, classNm, schoolKind);
        }
    }

    // 4-1. 캘린더 월 그리드용 학사일정 - 기본은 해당 월(+그리드에 보이는 앞뒤 달
    // 날짜까지) 전체 학사일정을 다 보여준다. keyword를 넘기면 이름에 그 키워드가
    // 포함된 것만 걸러서 보고 싶을 때 쓸 수 있도록 남겨둠(예: "주간"만 보기).
    @GetMapping("/api/calendar-events")
    @ResponseBody
    public List<CalendarEventDto> getCalendarEventsApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "") String keyword) {

        return schoolService.getMonthlyEvents(atptCode, schoolCode, year, month, keyword);
    }

    // 4-2. 일정 이름으로 검색 - 학사일정은 매년 반복되는 이름이 많아서(예:
    // "기말고사") 전체 검색 결과를 다 보여주면 어느 해 것인지 헷갈리므로, 오늘
    // 날짜와 가장 가까운 단 하나만 찾아 반환한다. 프론트는 이 날짜로 캘린더
    // 화면을 이동시키는 용도로 쓴다. 못 찾으면 404.
    @GetMapping("/api/calendar-events/search")
    @ResponseBody
    public ResponseEntity<CalendarEventDto> searchNearestEventApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String keyword) {

        CalendarEventDto nearest = schoolService.findNearestEvent(atptCode, schoolCode, keyword);
        return nearest != null ? ResponseEntity.ok(nearest) : ResponseEntity.notFound().build();
    }

    // 4-3. 방학 D-Day - 오늘이 방학 중이면 며칠째인지(D+N), 아니면 다가올 방학(식)까지
    // 며칠 남았는지(D-N). 캘린더 페이지에서 학교 선택 시 배지로 보여준다. 못 찾으면 404.
    @GetMapping("/api/vacation-dday")
    @ResponseBody
    public ResponseEntity<VacationDdayDto> getVacationDdayApi(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode) {

        VacationDdayDto dday = schoolService.getVacationDday(atptCode, schoolCode);
        return dday != null ? ResponseEntity.ok(dday) : ResponseEntity.notFound().build();
    }
}
