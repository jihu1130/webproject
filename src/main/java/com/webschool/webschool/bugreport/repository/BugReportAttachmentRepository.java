package com.webschool.webschool.bugreport.repository;

import com.webschool.webschool.bugreport.domain.BugReportAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BugReportAttachmentRepository extends JpaRepository<BugReportAttachment, Long> {
    List<BugReportAttachment> findByBugReport_IdOrderBySortOrderAsc(Long bugReportId);
}
