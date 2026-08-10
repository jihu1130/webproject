package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    boolean existsByPost_IdAndReporter_Username(Long postId, String username);

    // 관리자용: 게시물별 신고 목록(최신순) - 신고자/사유 확인, 최근 신고일 계산에 사용
    List<PostReport> findByPost_IdOrderByCreatedAtDesc(Long postId);
}
