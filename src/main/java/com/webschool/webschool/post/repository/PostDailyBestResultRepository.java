package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostDailyBestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PostDailyBestResultRepository extends JpaRepository<PostDailyBestResult, Long> {
    boolean existsByResultDate(LocalDate resultDate);

    Page<PostDailyBestResult> findAllByOrderByResultDateDesc(Pageable pageable);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 콘텐츠/이력은 보존, 작성자 참조만 끊는다
    // (PostContestResultRepository.detachAuthor()와 동일한 패턴).
    @Modifying
    @Query("UPDATE PostDailyBestResult r SET r.author = null WHERE r.author.id = :userId")
    void detachAuthor(@Param("userId") Long userId);
}
