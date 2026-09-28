package com.webschool.webschool.user.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 출석 관리 상세(캘린더 보기, 2026-09-28 추가)의 한 칸. date가 null이면 그 달 1일 앞의 요일
// 맞춤용 빈 칸(달력 그리드 정렬 - attendance-calendar.js의 마이페이지 팝업과 동일한 방식).
@Getter
@Builder
public class AdminAttendanceDayDto {
    private LocalDate date;
    private boolean attended;
    private boolean future;
}
