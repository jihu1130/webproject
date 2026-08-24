package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface PostViewRepository extends JpaRepository<PostView, Long> {
    boolean existsByPost_IdAndIpAndViewedAtAfter(Long postId, String ip, LocalDateTime after);
}
