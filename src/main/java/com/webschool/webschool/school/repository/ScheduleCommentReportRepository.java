package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.ScheduleCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleCommentReportRepository extends JpaRepository<ScheduleCommentReport, Long> {
    boolean existsByComment_IdAndReporter_Username(Long commentId, String username);
}
