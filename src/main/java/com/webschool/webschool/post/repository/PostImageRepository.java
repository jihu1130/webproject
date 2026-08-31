package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPost_IdOrderBySortOrderAsc(Long postId);

    int countByPost_Id(Long postId);

    // 목록 화면 대표 이미지(썸네일) 배치 조회용 - 게시글별로 정렬해두면 서비스 단에서 게시글당
    // 첫 번째(sortOrder 최소)만 골라 쓸 수 있다(post_id in (...) 하나로 N+1 방지).
    List<PostImage> findByPost_IdInOrderByPost_IdAscSortOrderAsc(Collection<Long> postIds);
}
