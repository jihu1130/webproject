package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    // 연속 출석 스트릭 계산(AttendanceService) + 마이페이지 미니 캘린더 팝업 조회용 - 범위 안의
    // 출석 기록을 전부 가져와 서비스 단에서 날짜 Set으로 바꿔 처리한다(이 프로젝트에 날짜범위
    // JPQL 집계 선례가 없어 다른 리포지토리들처럼 "조회는 단순하게, 계산은 서비스에서" 패턴을 따름).
    List<AttendanceLog> findByUser_IdAndAttendanceDateBetween(Long userId, LocalDate start, LocalDate end);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 출석 이력은 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM AttendanceLog a WHERE a.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
