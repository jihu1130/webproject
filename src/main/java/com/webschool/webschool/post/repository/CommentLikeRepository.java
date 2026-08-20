package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);

    // 마이페이지 "좋아요" 탭 - 내가 좋아요한 댓글 목록(최신순). PostLikeRepository.
    // findByUser_IdOrderByCreatedAtDesc와 동일한 패턴.
    List<CommentLike> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // 버그 수정(N+1) - CommentReportRepository.findReportedCommentIds()와 동일한 이유/패턴.
    @Query("SELECT l.comment.id FROM CommentLike l WHERE l.comment.id IN :commentIds AND l.user.username = :username")
    List<Long> findLikedCommentIds(@Param("commentIds") List<Long> commentIds, @Param("username") String username);
}
