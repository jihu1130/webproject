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

    List<UserBlock> findByBlocker_IdOrderByCreatedAtDesc(Long blockerId);

    // 양방향 확인 - A가 B를 차단했든 B가 A를 차단했든, 둘 중 하나라도 지금 유효하면 서로 댓글을 못 단다.
    @Query("SELECT COUNT(b) > 0 FROM UserBlock b WHERE " +
           "((b.blocker.id = :userAId AND b.blocked.id = :userBId) OR (b.blocker.id = :userBId AND b.blocked.id = :userAId)) " +
           "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    boolean existsActiveBetween(@Param("userAId") Long userAId, @Param("userBId") Long userBId,
                                 @Param("now") LocalDateTime now);

    // 오늘의 한마디(ScheduleComment) 목록에서 내가 차단한 사람의 글을 걸러내기 위한 조회 - 한마디는
    // 특정 글 작성자가 없는 공유 게시판이라 작성 자체를 막을 수는 없고(UserBlockService 클래스 주석
    // 참고), 대신 "내 화면에서 보이지 않게" 필터링하는 용도로만 쓴다.
    @Query("SELECT b.blocked.id FROM UserBlock b WHERE b.blocker.id = :blockerId " +
           "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    List<Long> findActiveBlockedIds(@Param("blockerId") Long blockerId, @Param("now") LocalDateTime now);
}
