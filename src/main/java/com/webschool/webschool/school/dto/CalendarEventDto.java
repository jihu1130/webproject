package com.webschool.webschool.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventDto {
    private String date;      // YYYY-MM-DD
    private String eventName; // 학사일정명 (예: "기말고사 주간")
}
