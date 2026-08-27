package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);
}
