package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentBookmarkRepository extends JpaRepository<CommentBookmark, Long> {
    Optional<CommentBookmark> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);
}
