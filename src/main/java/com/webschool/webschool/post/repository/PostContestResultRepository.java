package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostContestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostContestResultRepository extends JpaRepository<PostContestResult, Long> {
    List<PostContestResult> findAllByOrderByWeekStartDescRankAsc();

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 콘테스트 수상 기록은 남기고 작성자 참조만 끊는다.
    @Modifying
    @Query("UPDATE PostContestResult r SET r.author = null WHERE r.author.id = :userId")
    void detachAuthor(@Param("userId") Long userId);
}
