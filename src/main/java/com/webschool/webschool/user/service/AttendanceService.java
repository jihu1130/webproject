package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.AttendanceLog;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.dto.AttendanceCheckInResult;
import com.webschool.webschool.user.repository.AttendanceLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 출석체크(todo.md 요구사항) - 매일 방문 시 포인트를 지급한다. UserPointService.award()의
// 일일 획득 상한(DAILY_CAP)과는 무관하게 항상 지급돼야 하므로(사용자 요청 - 다른 활동으로 이미
// 한도를 채운 날에도 출석 보너스는 받아야 함) PostContestService 우승 포인트와 동일하게
// awardBonus()를 쓴다.
//
// 연속 출석 스트릭 보너스(디자인 개선 계획 - 출석체크 누적 포인트, 2026-09-09 추가) - 1일차 10P부터
// 시작해 이틀마다 2P씩 늘고, 6일차부터는 UserPointService.DAILY_CAP(30)과 같은 값으로 고정한다
// ("6일 연속 출석만으로 하루 상한을 채운다"는 의미). 하루라도 빠지면 1일차(10P)로 리셋. User나
// AttendanceLog에 "현재 스트릭" 컬럼을 별도로 저장/캐싱하지 않고 매번 AttendanceLog 날짜만으로
// 계산한다 - PointTier가 User.points에서 매번 등급을 계산하는 것과 동일한 철학으로, 스키마 변경도
// 스트릭-값 동기화 버그 걱정도 없다.
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final int[] STREAK_POINTS = {10, 12, 14, 16, 18};
    public static final int PLATEAU_POINTS = 30;

    // 스트릭 계산용 조회 범위 - 포인트 계산 자체는 6일치 이력만 있어도 충분하지만(7일차부터 고정값),
    // 화면에 "N일 연속 출석 중"처럼 실제 스트릭 숫자를 보여주려면 더 넉넉히 봐야 한다.
    private static final int STREAK_LOOKUP_DAYS = 90;

    private final AttendanceLogRepository attendanceLogRepository;
    private final UserPointService userPointService;

    // 오늘 이미 체크인했으면 checkedIn=false를 반환하고 아무것도 하지 않는다(에러가 아니라 "이미
    // 했음" 상태로 처리 - 버튼 중복 클릭/새로고침에도 안전).
    @Transactional
    public AttendanceCheckInResult checkIn(User user) {
        LocalDate today = LocalDate.now();
        Set<LocalDate> recentDates = loadRecentDates(user.getId(), today);

        if (recentDates.contains(today)) {
            return AttendanceCheckInResult.builder()
                    .checkedIn(false)
                    .streakDay(countConsecutiveDaysEnding(recentDates, today))
                    .pointsAwarded(0)
                    .build();
        }

        int streakDay = countConsecutiveDaysEnding(recentDates, today.minusDays(1)) + 1;

        AttendanceLog log = new AttendanceLog();
        log.setUser(user);
        log.setAttendanceDate(today);
        attendanceLogRepository.save(log);

        int points = pointsForStreakDay(streakDay);
        userPointService.awardBonus(user, points, "출석체크(" + streakDay + "일 연속)");

        return AttendanceCheckInResult.builder()
                .checkedIn(true)
                .streakDay(streakDay)
                .pointsAwarded(points)
                .build();
    }

    public boolean hasCheckedInToday(Long userId) {
        return attendanceLogRepository.existsByUserIdAndAttendanceDate(userId, LocalDate.now());
    }

    // 아직 오늘 체크인 전이면 "지금 체크인하면 며칠째가 되는지"(마이페이지 버튼의 "+N일차" 미리보기용),
    // 이미 체크인했다면 "오늘이 며칠째였는지"를 반환한다.
    public int getCurrentStreakDay(Long userId) {
        LocalDate today = LocalDate.now();
        Set<LocalDate> recentDates = loadRecentDates(userId, today);
        if (recentDates.contains(today)) {
            return countConsecutiveDaysEnding(recentDates, today);
        }
        return countConsecutiveDaysEnding(recentDates, today.minusDays(1)) + 1;
    }

    public int pointsForStreakDay(int streakDay) {
        if (streakDay <= STREAK_POINTS.length) {
            return STREAK_POINTS[streakDay - 1];
        }
        return PLATEAU_POINTS;
    }

    // 마이페이지 미니 캘린더 팝업(GET /mypage/attendance/calendar)용 - 해당 연/월에 출석한 날짜만.
    public List<LocalDate> getAttendedDatesInMonth(Long userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return attendanceLogRepository.findByUser_IdAndAttendanceDateBetween(userId, start, end)
                .stream()
                .map(AttendanceLog::getAttendanceDate)
                .toList();
    }

    private Set<LocalDate> loadRecentDates(Long userId, LocalDate today) {
        LocalDate start = today.minusDays(STREAK_LOOKUP_DAYS);
        return attendanceLogRepository.findByUser_IdAndAttendanceDateBetween(userId, start, today)
                .stream()
                .map(AttendanceLog::getAttendanceDate)
                .collect(Collectors.toSet());
    }

    // endInclusive부터 거꾸로 며칠이나 끊기지 않고 이어지는지(endInclusive 포함) 센다.
    private int countConsecutiveDaysEnding(Set<LocalDate> dates, LocalDate endInclusive) {
        int streak = 0;
        LocalDate cursor = endInclusive;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
