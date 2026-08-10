package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    boolean existsByComment_IdAndReporter_Username(Long commentId, String username);
}
