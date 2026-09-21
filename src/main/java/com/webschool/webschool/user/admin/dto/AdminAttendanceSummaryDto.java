package com.webschool.webschool.user.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 출석 관리 목록(관리자 페이지 재구성, 2026-09-21 추가) - 사용자별 출석 현황 한 줄.
@Getter
@Builder
public class AdminAttendanceSummaryDto {
    private Long userId;
    private String username;
    private String nickname;
    private long totalCheckIns;
    private int currentStreakDay;
    private LocalDate lastCheckInDate;
}
