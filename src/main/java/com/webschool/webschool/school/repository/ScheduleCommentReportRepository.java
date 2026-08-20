package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.ScheduleCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleCommentReportRepository extends JpaRepository<ScheduleCommentReport, Long> {
    boolean existsByComment_IdAndReporter_Username(Long commentId, String username);

    // 마이페이지 "신고" 탭(한마디 서브탭) - 내가 신고한 한마디 목록(최신순).
    List<ScheduleCommentReport> findByReporter_IdOrderByCreatedAtDesc(Long reporterId);

    // 신고 취소용 - PostReportRepository.findByPost_IdAndReporter_Username()와 동일 패턴.
    Optional<ScheduleCommentReport> findByComment_IdAndReporter_Username(Long commentId, String username);
}
