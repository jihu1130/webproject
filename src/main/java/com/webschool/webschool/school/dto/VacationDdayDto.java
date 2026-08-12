package com.webschool.webschool.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacationDdayDto {
    private boolean inVacation; // true면 오늘이 이미 방학 기간 안에 있다는 뜻
    private String label;       // 일정명 (예: "여름방학", "여름방학식")
    private String targetDate;  // 기준 날짜(YYYY-MM-DD) - 방학 중이면 방학 시작일, 아니면 다가올 방학(식) 날짜
    private int dday;           // 방학 중이면 시작일로부터 지난 일수(D+N), 아니면 그 날짜까지 남은 일수(D-N)
}
