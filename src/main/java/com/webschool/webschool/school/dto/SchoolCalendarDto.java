package com.webschool.webschool.school.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolCalendarDto {
    private String date;                  // YYYYMMDD
    private List<TimetableDto> timetable; // 시간표 목록
    private String meal;                  // 급식 메뉴 (중식/석식 등)
    private String eventName;             // 학사일정명 (없으면 null)
}