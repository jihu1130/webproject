package com.webschool.webschool.bugreport.repository;

import com.webschool.webschool.bugreport.domain.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {
    List<BugReport> findAllByOrderByCreatedAtDesc();

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 문의 내역은 남기고 제출자 참조만 끊는다.
    // reporter는 이미 nullable(비로그인 제출 대응)이라 스키마 변경 없이 그대로 사용.
    @Modifying
    @Query("UPDATE BugReport b SET b.reporter = null WHERE b.reporter.id = :userId")
    void detachReporter(@Param("userId") Long userId);

    // 관리자 대시보드 KPI 타일 - "미답변 문의 수".
    long countByResolvedFalse();
}
