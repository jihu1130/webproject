package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPost_IdAndUser_Id(Long postId, Long userId);

    // 게시글 상세 조회 시 "내가 이미 좋아요를 눌렀는지" 표시용 - PostReportRepository의
    // existsByPost_IdAndReporter_Username과 동일하게 User를 따로 조회하지 않고 username으로 바로 판단
    boolean existsByPost_IdAndUser_Username(Long postId, String username);

    // 마이페이지 "좋아요" 탭 - 내가 좋아요한 게시글 목록(최신순, 검색어 필터링은 메모리에서 처리).
    // PostBookmarkRepository.findByUser_IdOrderByCreatedAtDesc와 동일한 패턴.
    List<PostLike> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 좋아요 행 자체를 지운다(게시글 likeCount는
    // 별도로 감소시키지 않음 - 이미 탈퇴 상태에서 오래 유지된 좋아요라 카운트 보정은 범위 밖).
    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
