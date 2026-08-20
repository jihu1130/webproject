package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    boolean existsByComment_IdAndReporter_Username(Long commentId, String username);

    // 버그 수정(N+1) - PostCommentService.getComments()가 댓글마다 existsBy...를 따로 호출하던 것을
    // 댓글 목록 전체에 대해 한 번에 조회하도록 배치 처리. 이 목록에 포함된 id만 "내가 신고한 댓글".
    @Query("SELECT r.comment.id FROM CommentReport r WHERE r.comment.id IN :commentIds AND r.reporter.username = :username")
    List<Long> findReportedCommentIds(@Param("commentIds") List<Long> commentIds, @Param("username") String username);
}
