package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 출석체크 결과(AttendanceService.checkIn()) - 이미 오늘 체크인했으면 checkedIn=false,
// pointsAwarded=0이고 streakDay만 참고용(오늘 기록된 연속일수)으로 채워서 반환한다.
@Getter
@Builder
public class AttendanceCheckInResult {
    private boolean checkedIn;
    private int streakDay;
    private int pointsAwarded;
}
