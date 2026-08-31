package com.webschool.webschool.bugreport.repository;

import com.webschool.webschool.bugreport.domain.InquiryReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryReplyRepository extends JpaRepository<InquiryReply, Long> {
    List<InquiryReply> findByBugReport_IdAndDeletedFalseOrderByCreatedAtAsc(Long bugReportId);
}
