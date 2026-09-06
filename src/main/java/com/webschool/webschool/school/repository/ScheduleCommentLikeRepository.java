package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.ScheduleCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleCommentLikeRepository extends JpaRepository<ScheduleCommentLike, Long> {
    Optional<ScheduleCommentLike> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);

    // 마이페이지 "좋아요" 탭(한마디) - 내가 좋아요한 오늘의 한마디 목록(최신순, ScheduleCommentBookmarkRepository와 동일 패턴)
    List<ScheduleCommentLike> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 좋아요는 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM ScheduleCommentLike l WHERE l.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
