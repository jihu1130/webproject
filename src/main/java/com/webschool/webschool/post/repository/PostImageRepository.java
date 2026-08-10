package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPost_IdOrderBySortOrderAsc(Long postId);

    int countByPost_Id(Long postId);
}
