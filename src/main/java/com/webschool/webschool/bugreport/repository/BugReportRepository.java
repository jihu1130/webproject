package com.webschool.webschool.bugreport.repository;

import com.webschool.webschool.bugreport.domain.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {
    List<BugReport> findAllByOrderByCreatedAtDesc();
}
