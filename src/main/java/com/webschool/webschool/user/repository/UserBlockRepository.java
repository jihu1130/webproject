package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    // 버그 수정: 예전엔 만료 여부와 무관하게 전부 반환해서, 기간제 차단이 만료된 뒤에도
    // 마이페이지 "차단 목록"에 마치 지금도 차단 중인 것처럼 계속 남아있었다(실제로는
    // existsActiveBetween()이 이미 만료로 판단해 댓글 작성을 막지 않는데, 목록에는
    // "OO까지"라는 과거 날짜가 여전히 떠서 "차단이 안 풀린다"/"차단했는데 댓글이 달린다"
    // 둘 다로 오해할 수 있었음 - findActiveBlockedUserIds()와 동일한 만료 조건으로 맞췄다.
    @Query("SELECT b FROM UserBlock b WHERE b.blocker.id = :blockerId " +
           "AND (b.expiresAt IS NULL OR b.expiresAt > :now) ORDER BY b.createdAt DESC")
    List<UserBlock> findActiveByBlocker_IdOrderByCreatedAtDesc(@Param("blockerId") Long blockerId,
                                                                 @Param("now") LocalDateTime now);

    // 양방향 확인 - A가 B를 차단했든 B가 A를 차단했든, 둘 중 하나라도 지금 유효하면 서로 댓글을 못 단다.
    @Query("SELECT COUNT(b) > 0 FROM UserBlock b WHERE " +
           "((b.blocker.id = :userAId AND b.blocked.id = :userBId) OR (b.blocker.id = :userBId AND b.blocked.id = :userAId)) " +
           "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    boolean existsActiveBetween(@Param("userAId") Long userAId, @Param("userBId") Long userBId,
                                 @Param("now") LocalDateTime now);

    // 오늘의 한마디 목록 필터링용(ScheduleCommentService.getComments()) - 내가 현재 활성 상태로
    // 차단 중인 사용자 id 목록만 조회(만료된 차단은 제외). 한마디는 특정 "글 작성자"가 없는 공유
    // 스레드라 assertNotBlocked(작성 차단)이 아니라 목록에서 걸러내는 방식으로 차단을 적용한다.
    @Query("SELECT b.blocked.id FROM UserBlock b WHERE b.blocker.id = :blockerId " +
           "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    List<Long> findActiveBlockedUserIds(@Param("blockerId") Long blockerId, @Param("now") LocalDateTime now);
}
