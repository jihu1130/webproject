package com.webschool.webschool.admin.repository;

import com.webschool.webschool.admin.domain.AdminActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
    // 목록 자체는 관리자 화면에서 메모리 필터링(AdminActionLogService.matches() 등, 다른 관리자
    // 목록 화면들과 동일한 패턴)하므로 리포지토리는 전체를 최신순으로 가져오는 것 하나면 충분하다.
    java.util.List<AdminActionLog> findAllByOrderByCreatedAtDesc();
}
