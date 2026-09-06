package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {
    Optional<PostBookmark> findByPost_IdAndUser_Id(Long postId, Long userId);

    boolean existsByPost_IdAndUser_Username(Long postId, String username);

    // 마이페이지 "북마크" 탭 - 내가 북마크한 게시글 목록(최신순, 검색어 필터링은 메모리에서 처리)
    List<PostBookmark> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 북마크는 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM PostBookmark b WHERE b.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
