package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 버그 수정(N+1) - CommentReportRepository.findReportedCommentIds()와 동일한 이유.
    // PostCommentService.getComments()가 댓글마다 existsBy...를 따로 호출하던 것을 목록 전체에 대해
    // 한 번에 배치 조회하도록 교체.
    @Query("SELECT l.comment.id FROM CommentLike l WHERE l.comment.id IN :commentIds AND l.user.username = :username")
    List<Long> findLikedCommentIds(@Param("commentIds") List<Long> commentIds, @Param("username") String username);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 좋아요는 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM CommentLike l WHERE l.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
