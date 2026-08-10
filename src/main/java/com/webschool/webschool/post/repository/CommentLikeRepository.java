package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByComment_IdAndUser_Id(Long commentId, Long userId);

    boolean existsByComment_IdAndUser_Username(Long commentId, String username);
}
