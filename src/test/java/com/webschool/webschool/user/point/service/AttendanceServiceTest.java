package com.webschool.webschool.user.point.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.point.domain.AttendanceLog;
import com.webschool.webschool.user.point.dto.AttendanceCheckInResult;
import com.webschool.webschool.user.point.repository.AttendanceLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 출석체크 회귀 테스트. 출석 포인트는 일일 한도를 건너뛰는 지급(awardBonus)이라, "하루 한 번"과 연속 출석
// 계산이 틀리면 한도 없이 포인트가 쌓인다. 연속 일수는 어디에도 저장하지 않고 출석 날짜만으로 매번 계산한다.
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private UserPointService userPointService;

    @InjectMocks private AttendanceService attendanceService;

    private User user;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("test1");
    }

    // 오늘 기준 며칠 전에 출석했는지로 기록을 만든다(0 = 오늘, 1 = 어제).
    private void attendedDaysAgo(int... daysAgo) {
        List<AttendanceLog> logs = new ArrayList<>();
        for (int d : daysAgo) {
            AttendanceLog log = new AttendanceLog();
            log.setUser(user);
            log.setAttendanceDate(today.minusDays(d));
            logs.add(log);
        }
        lenient().when(attendanceLogRepository.findByUser_IdAndAttendanceDateBetween(eq(1L), any(), any()))
                .thenReturn(logs);
    }

    // ---- 하루 한 번 ----

    @Test
    void firstCheckIn_startsStreakAtDayOne() {
        attendedDaysAgo();

        AttendanceCheckInResult result = attendanceService.checkIn(user);

        assertTrue(result.isCheckedIn());
        assertEquals(1, result.getStreakDay());
        assertEquals(10, result.getPointsAwarded());
        verify(attendanceLogRepository).save(any());
        verify(userPointService).awardBonus(eq(user), eq(10), anyString());
    }

    // 버튼 연타·새로고침 - 에러가 아니라 "이미 했음"으로 끝나고 포인트는 다시 주지 않는다.
    @Test
    void secondCheckInSameDay_awardsNothing() {
        attendedDaysAgo(0, 1);

        AttendanceCheckInResult result = attendanceService.checkIn(user);

        assertFalse(result.isCheckedIn());
        assertEquals(0, result.getPointsAwarded());
        assertEquals(2, result.getStreakDay());
        verify(attendanceLogRepository, never()).save(any());
        verify(userPointService, never()).awardBonus(any(), anyInt(), anyString());
    }

    // ---- 연속 출석 ----

    @Test
    void consecutiveDays_increaseReward() {
        attendedDaysAgo(1, 2, 3);

        AttendanceCheckInResult result = attendanceService.checkIn(user);

        assertEquals(4, result.getStreakDay());
        assertEquals(16, result.getPointsAwarded());
    }

    // 하루라도 빠지면 1일차로 돌아간다 - 그저께까지 5일 연속이었어도 어제를 건너뛰었으면 10P.
    @Test
    void missedDay_resetsStreak() {
        attendedDaysAgo(2, 3, 4, 5, 6);

        AttendanceCheckInResult result = attendanceService.checkIn(user);

        assertEquals(1, result.getStreakDay());
        assertEquals(10, result.getPointsAwarded());
    }

    @Test
    void rewardTable_plateausFromDaySix() {
        assertEquals(10, attendanceService.pointsForStreakDay(1));
        assertEquals(12, attendanceService.pointsForStreakDay(2));
        assertEquals(18, attendanceService.pointsForStreakDay(5));
        assertEquals(30, attendanceService.pointsForStreakDay(6));
        assertEquals(30, attendanceService.pointsForStreakDay(60));
    }

    // ---- 화면 표시용 계산 ----

    // 버튼 미리보기("지금 누르면 N일차")와 관리자 목록("지금 살아 있는 연속 일수")은 일부러 다르다:
    // 한 번도 출석 안 한 사용자는 미리보기 1, 실제 스트릭 0.
    @Test
    void previewAndEffectiveStreak_differForBrokenStreak() {
        attendedDaysAgo(3, 4);

        assertEquals(1, attendanceService.getCurrentStreakDay(1L));
        assertEquals(0, attendanceService.getEffectiveStreak(1L));
    }

    @Test
    void effectiveStreak_survivesUntilEndOfNextDay() {
        // 어제까지 3일 연속, 오늘은 아직 체크인 전 - 스트릭은 아직 끊기지 않았다.
        attendedDaysAgo(1, 2, 3);

        assertEquals(3, attendanceService.getEffectiveStreak(1L));
        assertEquals(4, attendanceService.getCurrentStreakDay(1L));
    }

    // ---- 관리자 수동 인정 ----

    @Test
    void adminGrant_rejectsFutureAndDuplicateDates() {
        assertThrows(IllegalArgumentException.class, () -> attendanceService.adminGrant(user, today.plusDays(1)));

        lenient().when(attendanceLogRepository.existsByUserIdAndAttendanceDate(1L, today.minusDays(2))).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> attendanceService.adminGrant(user, today.minusDays(2)));

        verify(attendanceLogRepository, never()).save(any());
        verify(userPointService, never()).awardBonus(any(), anyInt(), anyString());
    }

    // 지난 날짜를 인정할 때도 그 날짜 기준의 연속 일수로 포인트를 계산한다(오늘 기준이 아님).
    @Test
    void adminGrant_usesStreakAsOfThatDate() {
        // 인정할 날짜 = 2일 전. 그 전날(3일 전), 전전날(4일 전) 출석 기록이 있다 -> 3일차 = 14P.
        attendedDaysAgo(3, 4);

        int points = attendanceService.adminGrant(user, today.minusDays(2));

        assertEquals(14, points);
        verify(userPointService).awardBonus(eq(user), eq(14), anyString());
    }
}
