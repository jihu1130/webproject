package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    // 관리자용: 삭제된 댓글까지 전부 포함해서 조회
    List<PostComment> findByPost_IdOrderByCreatedAtAsc(Long postId);

    // 일반 사용자용: 삭제되지 않은 댓글만 조회
    List<PostComment> findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(Long postId);

    // 관리자용: 블라인드 처리됐거나 신고가 있는 댓글 (삭제된 댓글은 제외) - "신고 관리 > 댓글" 탭
    @Query("SELECT c FROM PostComment c WHERE c.deleted = false AND (c.blind = true OR c.reportCount > 0) "
            + "ORDER BY c.blind DESC, c.reportCount DESC, c.createdAt DESC")
    List<PostComment> findReportedOrBlindComments();
}
