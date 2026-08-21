package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findByUuid(String uuid); // 공개 URL(/posts/{uuid}) 조회용

    // 버그 수정 - 예전엔 PostService가 엔티티를 읽어서 count+1 계산 후 그대로 save()하는 방식이라, 두
    // 요청이 거의 동시에 들어오면(좋아요/조회수/신고 전부 해당) 하나의 +1이 사라지는 lost update가
    // 가능했다. DB 레벨에서 원자적으로 증감하는 벌크 UPDATE로 교체 - 호출부는 이 메서드 실행 후 반환
    // 값(응답용 표시 카운트)을 로컬 변수로 따로 계산해야 한다(엔티티 필드를 같이 건드리면 트랜잭션
    // 커밋 시점에 Hibernate dirty-check가 이 벌크 업데이트를 덮어써서 race가 재발할 수 있음).
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Post p SET p.reportCount = p.reportCount + 1 WHERE p.id = :id")
    void incrementReportCount(@Param("id") Long id);

    // 신고 취소용(cancelReport()) - incrementLikeCount()/decrementLikeCount() 쌍과 동일한 이유.
    @Modifying
    @Query("UPDATE Post p SET p.reportCount = CASE WHEN p.reportCount > 0 THEN p.reportCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementReportCount(@Param("id") Long id);

    // 커뮤니티 목록 - 카테고리 필터/검색어 둘 다 선택 사항(null이면 무시). scope로 검색 대상을
    // 제목만("title")/내용만("content")/작성자 닉네임만("author")/제목+내용(그 외 값 - 기본)으로
    // 좁힐 수 있다. 정렬은 쿼리에 고정하지 않고 Pageable의 Sort를 그대로 따른다(PostService.getList()
    // 에서 정렬 옵션에 맞는 Sort를 만들어 넘김) - "최신순/오래된순/조회수순" 같은 여러 정렬을 이
    // 메서드 하나로 지원하기 위함.
    // 작성자 검색(scope='author')은 익명(ANONYMOUS) 글을 항상 제외한다 - 포함시키면 "이 사람이 이
    // 익명 글을 썼다"는 게 검색으로 드러나서 익명 기능의 취지가 깨진다(findByAuthor_IdAndCategoryNot...
    // 공개 프로필 조회 메서드와 동일한 익명성 보호 원칙, CLAUDE.md 참고). 그래서 제목/내용 검색과
    // 다르게 OR 체인에 단순히 조건 하나를 추가하는 방식이 아니라 scope='author'일 때 통째로 분기한다.
    // p.visibility = PUBLIC - UNLISTED(링크 전용) 글은 목록/검색에서 항상 제외한다(Post.java 주석
    // 참고). 상세 페이지(GET /posts/{uuid})는 이 메서드를 거치지 않고 findByUuid()로 직접 조회하므로
    // uuid 링크만 있으면 그대로 열람 가능하다.
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND p.blind = false "
            + "AND p.visibility = com.webschool.webschool.post.domain.Post.Visibility.PUBLIC "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (" +
            "  (:scope = 'author' AND p.category <> com.webschool.webschool.post.domain.Post.Category.ANONYMOUS "
            + "     AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.author.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))) "
            + "  OR (:scope <> 'author' AND (:keyword IS NULL OR :keyword = '' "
            + "     OR (:scope <> 'content' AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "     OR (:scope <> 'title' AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))))" +
            ") ")
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
    // UNLISTED(링크 전용) 글도 제외 - 프로필 목록에 뜨면 "링크로만 공개"라는 취지가 깨진다.
    Page<Post> findByAuthor_IdAndCategoryNotAndDeletedFalseAndBlindFalseAndVisibilityOrderByCreatedAtDesc(
            Long authorId, Post.Category excludedCategory, Post.Visibility visibility, Pageable pageable);

    // 마이페이지("내 활동내역")용 - 본인이 직접 보는 화면이라 ANONYMOUS 카테고리/블라인드 글도
    // 그대로 포함한다(공개 프로필용 메서드와 달리 익명성 보호가 필요 없음 - 본인이 본인 글 보는 것).
    // 검색어 필터링은 관리자 목록 화면들과 동일하게 메모리에서 처리하므로(MyActivityService.matches()
    // 참고, 본인 글만 대상이라 규모가 작다고 가정) Pageable 없는 List 버전으로 전체를 가져온다.
    List<Post> findByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(Long authorId);
}
