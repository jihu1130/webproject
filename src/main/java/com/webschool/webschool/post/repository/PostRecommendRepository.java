package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostRecommend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRecommendRepository extends JpaRepository<PostRecommend, Long> {
    boolean existsByPost_IdAndVoter_Id(Long postId, Long voterId);

    long countByPost_Id(Long postId);

    long countByPost_IdAndCreatedAtBetween(Long postId, LocalDateTime start, LocalDateTime end);

    // 일간/주간/월간 랭킹 - FREE/PUBLIC/미삭제/비블라인드 게시글로 좁힌 뒤 기간 내 추천수로
    // 집계한다(정렬은 서비스에서 처리 - JPQL의 COUNT 별칭 정렬이 Hibernate 버전별로 까다로워
    // 피함). 전체(초기화 없는) 랭킹은 기간 조건 없이 동일 쿼리를 재사용한다(start=최소값,
    // end=최대값으로 호출).
    @Query("SELECT r.post.id AS postId, COUNT(r) AS count FROM PostRecommend r "
            + "WHERE r.createdAt BETWEEN :start AND :end "
            + "AND r.post.category = com.webschool.webschool.post.domain.Post.Category.FREE "
            + "AND r.post.visibility = com.webschool.webschool.post.domain.Post.Visibility.PUBLIC "
            + "AND r.post.deleted = false AND r.post.blind = false "
            + "GROUP BY r.post.id")
    List<PostRecommendCount> countByPostGroupedInRange(@Param("start") LocalDateTime start,
                                                         @Param("end") LocalDateTime end);

    // 매일 자정 배치(PostRecommendService.tallyPreviousDay())가 전날 1위를 뽑을 때 쓰는 쿼리 -
    // 위 countByPostGroupedInRange()와 동일한 대상 조건.
    @Query("SELECT r.post.id AS postId, COUNT(r) AS count FROM PostRecommend r "
            + "WHERE r.createdAt >= :dayStart AND r.createdAt < :dayEnd "
            + "AND r.post.category = com.webschool.webschool.post.domain.Post.Category.FREE "
            + "AND r.post.visibility = com.webschool.webschool.post.domain.Post.Visibility.PUBLIC "
            + "AND r.post.deleted = false AND r.post.blind = false "
            + "GROUP BY r.post.id")
    List<PostRecommendCount> countByPostGroupedForDay(@Param("dayStart") LocalDateTime dayStart,
                                                        @Param("dayEnd") LocalDateTime dayEnd);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 추천은 개인 활동 흔적이라 행 자체를 지운다.
    @Modifying
    @Query("DELETE FROM PostRecommend r WHERE r.voter.id = :userId")
    void deleteAllByVoterId(@Param("userId") Long userId);
}
