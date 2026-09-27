package com.webschool.webschool.school.controller;

import com.webschool.webschool.school.dto.PersonalEventDto;
import com.webschool.webschool.school.service.PersonalEventService;
import com.webschool.webschool.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 개인 전용 일정 - 나만 볼 수 있는 캘린더 메모(학교/학년/반과 무관). 항상 로그인 필요
// (SecurityConfig의 /school/api/personal-events/** 규칙 참고). 2026-09-28 SchoolController에서 분리.
@Controller
@RequestMapping("/school")
@RequiredArgsConstructor
public class PersonalEventController {

    private final PersonalEventService personalEventService;

    @GetMapping("/api/personal-events")
    @ResponseBody
    public List<PersonalEventDto> getPersonalEvents(@RequestParam String date, Authentication authentication) {
        return personalEventService.getEventsForDate(authentication.getName(), parseDate(date));
    }

    // 월 그리드에 점으로 표시할 날짜 목록
    @GetMapping("/api/personal-events/month")
    @ResponseBody
    public List<String> getPersonalEventMonthDots(@RequestParam int year, @RequestParam int month,
                                                    Authentication authentication) {
        return personalEventService.getEventDatesInRange(authentication.getName(), year, month);
    }

    @PostMapping("/api/personal-events")
    @ResponseBody
    public PersonalEventDto createPersonalEvent(@RequestParam String date, @RequestParam String title,
                                                 @RequestParam(required = false) String memo,
                                                 Authentication authentication) {
        return personalEventService.createEvent(authentication.getName(), parseDate(date), title, memo);
    }

    @PutMapping("/api/personal-events/{id}")
    @ResponseBody
    public PersonalEventDto updatePersonalEvent(@PathVariable Long id, @RequestParam String title,
                                                 @RequestParam(required = false) String memo,
                                                 Authentication authentication) {
        return personalEventService.updateEvent(id, authentication.getName(), title, memo);
    }

    @DeleteMapping("/api/personal-events/{id}")
    @ResponseBody
    public Map<String, Object> deletePersonalEvent(@PathVariable Long id, Authentication authentication) {
        personalEventService.deleteEvent(id, authentication.getName());
        return Map.of("deleted", true);
    }

    private LocalDate parseDate(String date) {
        return SchoolService.parseYmd(date);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
