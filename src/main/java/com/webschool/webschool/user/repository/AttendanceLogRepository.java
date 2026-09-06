package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 출석 이력은 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM AttendanceLog a WHERE a.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
