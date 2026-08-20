package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    // PostRepository.incrementLikeCount()와 동일한 이유(lost update 방지) - 벌크 UPDATE로 원자적 증감.
    @Modifying
    @Query("UPDATE PostComment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PostComment c SET c.likeCount = CASE WHEN c.likeCount > 0 THEN c.likeCount - 1 ELSE 0 END WHERE c.id = :id")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PostComment c SET c.reportCount = c.reportCount + 1 WHERE c.id = :id")
    void incrementReportCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PostComment c SET c.reportCount = CASE WHEN c.reportCount > 0 THEN c.reportCount - 1 ELSE 0 END WHERE c.id = :id")
    void decrementReportCount(@Param("id") Long id);

    // 관리자용: 삭제된 댓글까지 전부 포함해서 조회
    List<PostComment> findByPost_IdOrderByCreatedAtAsc(Long postId);

    // 일반 사용자용: 삭제되지 않은 댓글만 조회. 버그 수정(N+1) - author가 LAZY라 댓글마다 작성자를
    // 따로 조회했는데, @EntityGraph로 한 번의 JOIN FETCH에 묶었다(PostCommentService.getComments()의
    // 신고/좋아요/북마크 여부 배치 조회와 함께 적용해야 진짜 효과가 있음).
    @EntityGraph(attributePaths = "author")
    List<PostComment> findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(Long postId);

    // 관리자용: 블라인드 처리됐거나 신고가 있는 댓글 (삭제된 댓글은 제외) - "신고 관리 > 댓글" 탭
    @Query("SELECT c FROM PostComment c WHERE c.deleted = false AND (c.blind = true OR c.reportCount > 0) "
            + "ORDER BY c.blind DESC, c.reportCount DESC, c.createdAt DESC")
    List<PostComment> findReportedOrBlindComments();

    // 관리자용: 삭제되지 않은 전체 댓글 목록(신고 여부 무관) - "댓글 관리" 전체 탭
    // (게시글의 "전체 게시글" 탭과 동일한 이유로 추가 - 평소 문제 없는 댓글도 관리자가 미리 둘러볼 수 있어야 함)
    List<PostComment> findAllByDeletedFalseOrderByCreatedAtDesc();

    // 관리자용: 소프트 삭제된 댓글 목록 (최근 삭제순) - "댓글 관리" 삭제됨 탭
    List<PostComment> findAllByDeletedTrueOrderByDeletedAtDesc();

    // 관리자용: 게시글 목록에서 "이 글에 신고된 댓글이 있는지" 배지를 보여주기 위한 카운트
    // (게시물 자체는 신고가 없어도 그 밑에 신고된 댓글이 있을 수 있는데, 기존엔 "전체 게시글" 탭만
    // 봐서는 알 방법이 없었다 - 신고 관리 > 댓글 탭에서 따로 확인해야 했음)
    @Query("SELECT COUNT(c) FROM PostComment c WHERE c.post.id = :postId AND c.deleted = false "
            + "AND (c.blind = true OR c.reportCount > 0)")
    long countReportedOrBlindByPostId(@Param("postId") Long postId);

    // 관리자용: 계정 프로필 화면 - 작성 댓글 수
    long countByAuthor_IdAndDeletedFalse(Long authorId);

    // 마이페이지("내 활동내역")용 - 본인이 작성한 댓글 목록 (검색어 필터링은 메모리에서 처리)
    List<PostComment> findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(Long authorId);

    // QnA 답변 채택(네이버 지식인 스타일) - 게시글당 채택된 답변은 항상 최대 1개라
    // 새로 채택하기 전에 기존 채택을 먼저 찾아 해제해야 한다.
    Optional<PostComment> findByPost_IdAndAcceptedTrue(Long postId);
}
