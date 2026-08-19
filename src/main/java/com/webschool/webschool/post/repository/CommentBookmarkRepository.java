package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentBookmarkRepository extends JpaRepository<CommentBookmark, Long> {
    Optional<CommentBookmark> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);

    // 마이페이지 "북마크" 탭 - 내가 북마크한 댓글 목록(최신순). PostBookmarkRepository.
    // findByUser_IdOrderByCreatedAtDesc와 동일한 패턴.
    List<CommentBookmark> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
