package com.webschool.webschool.user.admin.service;

import com.webschool.webschool.user.admin.dto.AdminAttendanceSummaryDto;
import com.webschool.webschool.user.domain.AttendanceLog;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.AttendanceLogRepository;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// 관리자가 다른 사용자의 출석 현황을 "조회만" 하는 책임 - AttendanceService(본인 출석체크 처리)와
// 분리한다(CLAUDE.md 컨벤션: 다른 사람 것을 조회만 하는 책임은 새 서비스로 뽑는 게 관례).
@Service
@RequiredArgsConstructor
public class AdminAttendanceService {

    private final UserRepository userRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceService attendanceService;

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
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return toSummaryDto(user);
    }

    public List<LocalDate> getAttendedDatesInMonth(Long userId, int year, int month) {
        return attendanceService.getAttendedDatesInMonth(userId, year, month);
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
