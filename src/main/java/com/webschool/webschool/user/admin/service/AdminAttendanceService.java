package com.webschool.webschool.user.admin.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.user.admin.dto.AdminAttendanceDayDto;
import com.webschool.webschool.user.admin.dto.AdminAttendanceSummaryDto;
import com.webschool.webschool.user.point.domain.AttendanceLog;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.point.repository.AttendanceLogRepository;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.point.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 관리자가 다른 사용자의 출석 현황을 "조회만" 하는 책임 - AttendanceService(본인 출석체크 처리)와
// 분리한다(CLAUDE.md 컨벤션: 다른 사람 것을 조회만 하는 책임은 새 서비스로 뽑는 게 관례).
@Service
@RequiredArgsConstructor
public class AdminAttendanceService {

    private final UserRepository userRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceService attendanceService;
    private final AdminActionLogService adminActionLogService;

    // 다른 관리자 목록(AdminUserService.getAllUsers() 등)과 동일하게 전체를 메모리에서 훑어
    // 필터링한다 - 이 프로젝트 관리자 화면의 기존 관례(PageUtils 참고), 데이터 규모가 작다고 가정.
    public List<AdminAttendanceSummaryDto> getAllAttendanceSummaries(String keyword) {
        return userRepository.findAllByOrderByIdAsc().stream()
                .filter(user -> !user.isDeleted())
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getUsername(), dto.getNickname()))
                .toList();
    }

    public AdminAttendanceSummaryDto getSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toSummaryDto(user);
    }

    public List<LocalDate> getAttendedDatesInMonth(Long userId, int year, int month) {
        return attendanceService.getAttendedDatesInMonth(userId, year, month);
    }

    // 출석 관리 상세 캘린더 그리드(2026-09-28 추가) - 마이페이지 미니 캘린더 팝업(attendance-calendar.js)과
    // 동일하게 1일의 요일만큼 빈 칸을 앞에 채운 뒤 날짜 칸을 이어붙인다. 여긴 서버 렌더링(관리자 액션
    // 버튼을 th:action 폼으로 그려야 해서 JS 위젯 대신 Thymeleaf로 직접 그림)이라 서비스 단에서
    // 그리드 자체를 미리 구성해서 넘긴다.
    public List<AdminAttendanceDayDto> getMonthGrid(Long userId, int year, int month) {
        Set<LocalDate> attended = Set.copyOf(attendanceService.getAttendedDatesInMonth(userId, year, month));
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = LocalDate.of(year, month, 1);
        int leadingBlanks = firstOfMonth.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : firstOfMonth.getDayOfWeek().getValue();

        List<AdminAttendanceDayDto> grid = new ArrayList<>();
        for (int i = 0; i < leadingBlanks; i++) {
            grid.add(AdminAttendanceDayDto.builder().build());
        }
        for (int day = 1; day <= firstOfMonth.lengthOfMonth(); day++) {
            LocalDate date = firstOfMonth.withDayOfMonth(day);
            grid.add(AdminAttendanceDayDto.builder()
                    .date(date)
                    .attended(attended.contains(date))
                    .future(date.isAfter(today))
                    .build());
        }
        return grid;
    }

    // durationDays 없이 "그 날짜"만 받는 즉시 처리(요청 즉시 실행, 승인 대기 개념 없음).
    public void grantAttendance(Long userId, LocalDate date, String actingAdminUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        int points = attendanceService.adminGrant(user, date);
        adminActionLogService.log("USER", userId, "ATTENDANCE_GRANT",
                date + " 출석 인정 (+" + points + "P)", actingAdminUsername);
    }

    public void revokeAttendance(Long userId, LocalDate date, String actingAdminUsername) {
        attendanceService.adminRevoke(userId, date);
        adminActionLogService.log("USER", userId, "ATTENDANCE_REVOKE", date + " 출석 취소", actingAdminUsername);
    }

    private AdminAttendanceSummaryDto toSummaryDto(User user) {
        long totalCheckIns = attendanceLogRepository.countByUserId(user.getId());
        LocalDate lastCheckIn = attendanceLogRepository.findTopByUserIdOrderByAttendanceDateDesc(user.getId())
                .map(AttendanceLog::getAttendanceDate)
                .orElse(null);
        int currentStreak = attendanceService.getEffectiveStreak(user.getId());
        return AdminAttendanceSummaryDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .totalCheckIns(totalCheckIns)
                .currentStreakDay(currentStreak)
                .lastCheckInDate(lastCheckIn)
                .build();
    }

    private boolean matches(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase();
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }
}
