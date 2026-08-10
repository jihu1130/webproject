package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findByUuid(String uuid); // 공개 URL(/posts/{uuid}) 조회용


    // 커뮤니티 목록 - 카테고리 필터/검색어(제목+내용) 둘 다 선택 사항(null이면 무시)
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND p.blind = false "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:keyword IS NULL OR :keyword = '' "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "ORDER BY p.createdAt DESC")
    Page<Post> search(@Param("category") Post.Category category, @Param("keyword") String keyword, Pageable pageable);

    // 관리자용: 블라인드 처리된 글 + 신고가 누적됐지만 아직 블라인드되지 않은 글을 함께 조회 (삭제된 글은 "삭제됨" 탭에서 별도 조회)
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND (p.blind = true OR p.reportCount > 0) "
            + "ORDER BY p.blind DESC, p.reportCount DESC, p.createdAt DESC")
    List<Post> findReportedOrBlindPosts();

    // 관리자용: 소프트 삭제된 글 목록 (최근 삭제순)
    List<Post> findAllByDeletedTrueOrderByDeletedAtDesc();

    // 관리자용: 삭제되지 않은 전체 글 목록(신고 여부 무관) - "전체 게시글" 탭
    List<Post> findAllByDeletedFalseOrderByCreatedAtDesc();
}
