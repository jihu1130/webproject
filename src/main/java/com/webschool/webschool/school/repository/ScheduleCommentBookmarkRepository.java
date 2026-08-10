package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.ScheduleCommentBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleCommentBookmarkRepository extends JpaRepository<ScheduleCommentBookmark, Long> {
    Optional<ScheduleCommentBookmark> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);

    // 마이페이지 "북마크" 탭(한마디) - 내가 북마크한 오늘의 한마디 목록(최신순, 검색어 필터링은 메모리에서 처리)
    List<ScheduleCommentBookmark> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
