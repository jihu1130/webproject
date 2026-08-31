package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.AttendanceLog;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.AttendanceLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 출석체크(todo.md 요구사항) - 매일 방문 시 기본 포인트를 지급한다. UserPointService.award()의
// 일일 획득 상한(DAILY_CAP)과는 무관하게 항상 지급돼야 하므로(사용자 요청 - 다른 활동으로 이미
// 한도를 채운 날에도 출석 보너스는 받아야 함) PostContestService 우승 포인트와 동일하게
// awardBonus()를 쓴다.
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final int ATTENDANCE_POINTS = 10;

    private final AttendanceLogRepository attendanceLogRepository;
    private final UserPointService userPointService;

    // 오늘 이미 체크인했으면 false를 반환하고 아무것도 하지 않는다(에러가 아니라 "이미 했음" 상태로
    // 처리 - 버튼 중복 클릭/새로고침에도 안전).
    @Transactional
    public boolean checkIn(User user) {
        LocalDate today = LocalDate.now();
        if (attendanceLogRepository.existsByUserIdAndAttendanceDate(user.getId(), today)) {
            return false;
        }

        AttendanceLog log = new AttendanceLog();
        log.setUser(user);
        log.setAttendanceDate(today);
        attendanceLogRepository.save(log);

        userPointService.awardBonus(user, ATTENDANCE_POINTS, "출석체크");
        return true;
    }

    public boolean hasCheckedInToday(Long userId) {
        return attendanceLogRepository.existsByUserIdAndAttendanceDate(userId, LocalDate.now());
    }
}
