package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 마이페이지 출석 미니 캘린더 팝업(GET /mypage/attendance/calendar) 응답 - attendedDates는
// "yyyy-MM-dd" 문자열 목록(해당 연/월 안에서 출석한 날짜만), 프론트에서 그리드 셀과 매칭하기 쉽게
// LocalDate 대신 문자열로 직렬화한다.
@Getter
@Builder
public class AttendanceCalendarDto {
    private List<String> attendedDates;
    private int currentStreakDay;
    private boolean checkedInToday;
}
