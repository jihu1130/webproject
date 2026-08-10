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


    // 커뮤니티 목록 - 카테고리 필터/검색어 둘 다 선택 사항(null이면 무시). scope로 검색 대상을
    // 제목만("title")/내용만("content")/제목+내용(그 외 값 - 기본)으로 좁힐 수 있다. 정렬은 쿼리에
    // 고정하지 않고 Pageable의 Sort를 그대로 따른다(PostService.getList()에서 정렬 옵션에 맞는
    // Sort를 만들어 넘김) - "최신순/오래된순/조회수순" 같은 여러 정렬을 이 메서드 하나로 지원하기 위함.
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND p.blind = false "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:keyword IS NULL OR :keyword = '' "
            + "     OR (:scope <> 'content' AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "     OR (:scope <> 'title' AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))) ")
    Page<Post> search(@Param("category") Post.Category category, @Param("keyword") String keyword,
                       @Param("scope") String scope, Pageable pageable);

    // 관리자용: 블라인드 처리된 글 + 신고가 누적됐지만 아직 블라인드되지 않은 글을 함께 조회 (삭제된 글은 "삭제됨" 탭에서 별도 조회)
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND (p.blind = true OR p.reportCount > 0) "
            + "ORDER BY p.blind DESC, p.reportCount DESC, p.createdAt DESC")
    List<Post> findReportedOrBlindPosts();

    // 관리자용: 소프트 삭제된 글 목록 (최근 삭제순)
    List<Post> findAllByDeletedTrueOrderByDeletedAtDesc();

    // 관리자용: 삭제되지 않은 전체 글 목록(신고 여부 무관) - "전체 게시글" 탭
    List<Post> findAllByDeletedFalseOrderByCreatedAtDesc();

    // 관리자용: 계정 프로필 화면 - 작성 글 수 / 최근 작성 글 미리보기
    long countByAuthor_IdAndDeletedFalse(Long authorId);

    List<Post> findTop5ByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(Long authorId);

    // 공개 프로필(/users/{id})용 "작성한 게시글" 목록. 익명(ANONYMOUS) 카테고리 글은 본인 프로필에서도
    // 제외한다 - 포함시키면 "이 사람이 이 익명 글을 썼다"는 게 드러나서 익명 기능의 취지가 깨진다
    // (커뮤니티 검색에 작성자 닉네임 검색을 일부러 안 넣은 것과 같은 이유 - CLAUDE.md 참고).
    // 신고 누적으로 블라인드된 글도 제외(공개 커뮤니티 목록과 동일한 가시성 규칙, PostRepository.search() 참고).
    Page<Post> findByAuthor_IdAndCategoryNotAndDeletedFalseAndBlindFalseOrderByCreatedAtDesc(
            Long authorId, Post.Category excludedCategory, Pageable pageable);

    // 마이페이지("내 활동내역")용 - 본인이 직접 보는 화면이라 ANONYMOUS 카테고리/블라인드 글도
    // 그대로 포함한다(공개 프로필용 메서드와 달리 익명성 보호가 필요 없음 - 본인이 본인 글 보는 것).
    // 검색어 필터링은 관리자 목록 화면들과 동일하게 메모리에서 처리하므로(MyActivityService.matches()
    // 참고, 본인 글만 대상이라 규모가 작다고 가정) Pageable 없는 List 버전으로 전체를 가져온다.
    List<Post> findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(Long authorId);
}
