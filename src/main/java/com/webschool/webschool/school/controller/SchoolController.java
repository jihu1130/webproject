package com.webschool.webschool.school.controller;

import com.webschool.webschool.school.dto.ScheduleCommentDto;
import com.webschool.webschool.school.dto.ScheduleCommentReportResultDto;
import com.webschool.webschool.school.dto.SchoolCalendarDto;
import com.webschool.webschool.school.dto.SchoolSearchResultDto;
import com.webschool.webschool.school.dto.TimetableDto;
import com.webschool.webschool.school.service.NeisApiService;
import com.webschool.webschool.school.service.ScheduleCommentService;
import com.webschool.webschool.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/school") // 👈 기본 경로가 /school 로 잡혀있는지 확인!
@RequiredArgsConstructor
public class SchoolController {

    private final NeisApiService neisApiService;
    private final SchoolService schoolService;
    private final ScheduleCommentService scheduleCommentService;

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
            @RequestParam(defaultValue = "1") String classNm) {

        try {
            return neisApiService.fetchTimetableFromNeis(atptCode, schoolCode, date, grade, classNm);
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
            @RequestParam(defaultValue = "1") String classNm) {

        return schoolService.getCalendarDetails(atptCode, schoolCode, date, grade, classNm);
    }

    // 5. 날짜별 한마디 댓글 조회 (같은 학년·같은 반끼리만 공유)
    @GetMapping("/api/comments")
    @ResponseBody
    public List<ScheduleCommentDto> getComments(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") String grade,
            @RequestParam(defaultValue = "1") String classNm,
            Authentication authentication) {

        return scheduleCommentService.getComments(atptCode, schoolCode, parseDate(date), grade, classNm, authentication.getName());
    }

    // 6. 날짜별 한마디 댓글 작성
    @PostMapping("/api/comments")
    @ResponseBody
    public ScheduleCommentDto createComment(
            @RequestParam(defaultValue = "N10") String atptCode,
            @RequestParam(defaultValue = "8181104") String schoolCode,
            @RequestParam String date,
            @RequestParam(defaultValue = "1") String grade,
            @RequestParam(defaultValue = "1") String classNm,
            @RequestParam String content,
            Authentication authentication) {

        return scheduleCommentService.createComment(atptCode, schoolCode, parseDate(date), grade, classNm, authentication.getName(), content);
    }

    // 7. 댓글 수정 (본인 작성 댓글만)
    @PutMapping("/api/comments/{id}")
    @ResponseBody
    public ScheduleCommentDto updateComment(@PathVariable Long id, @RequestParam String content, Authentication authentication) {
        return scheduleCommentService.updateComment(id, authentication.getName(), content);
    }

    // 8. 댓글 삭제 (본인 작성 댓글만)
    @DeleteMapping("/api/comments/{id}")
    @ResponseBody
    public Map<String, Object> deleteComment(@PathVariable Long id, Authentication authentication) {
        scheduleCommentService.deleteComment(id, authentication.getName());
        return Map.of("deleted", true);
    }

    // 9. 댓글 신고 - 서로 다른 사용자 3명이 신고하면 자동 블라인드 (PostCommentController와 동일 패턴)
    @PostMapping("/api/comments/{id}/report")
    @ResponseBody
    public ScheduleCommentReportResultDto reportComment(@PathVariable Long id,
                                                          @RequestParam(required = false) String reason,
                                                          Authentication authentication) {
        return scheduleCommentService.reportComment(id, authentication.getName(), reason);
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}