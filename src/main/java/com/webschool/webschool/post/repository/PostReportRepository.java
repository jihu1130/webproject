package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    boolean existsByPost_IdAndReporter_Username(Long postId, String username);

    // 관리자용: 게시물별 신고 목록(최신순) - 신고자/사유 확인, 최근 신고일 계산에 사용
    List<PostReport> findByPost_IdOrderByCreatedAtDesc(Long postId);

    // 마이페이지 "신고" 탭(게시글 서브탭) - 내가 신고한 게시글 목록(최신순).
    List<PostReport> findByReporter_IdOrderByCreatedAtDesc(Long reporterId);

    // 신고 취소용 - 본인이 신고한 그 신고 row를 찾아서 삭제한다(UserBlockService.unblock()과 동일 패턴).
    Optional<PostReport> findByPost_IdAndReporter_Username(Long postId, String username);
}
