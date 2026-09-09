package com.webschool.webschool.weather.controller;

import com.webschool.webschool.weather.dto.WeatherWeekDto;
import com.webschool.webschool.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 캘린더에 선택된 학교 기준 이번 주 날씨 위젯 조회. 게시판 조회 패턴과 동일하게 비로그인도
// 열람 가능(SecurityConfig에서 이 경로를 GET permitAll로 열어둠, Feature 3).
@RestController
@RequestMapping("/school")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/api/weather")
    public ResponseEntity<WeatherWeekDto> getWeekWeather(@RequestParam String atptCode,
                                                           @RequestParam String schoolCode) {
        WeatherWeekDto week = weatherService.getWeekWidgetForSchool(atptCode, schoolCode);
        if (week == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(week);
    }
}
