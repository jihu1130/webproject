package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostContestVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PostContestVoteRepository extends JpaRepository<PostContestVote, Long> {
    List<PostContestVote> findByWeekStart(LocalDate weekStart);

    List<PostContestVote> findByEntry_Id(Long entryId);

    boolean existsByVoter_IdAndWeekStart(Long voterId, LocalDate weekStart);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 콘테스트 투표는 개인 활동 흔적이라 함께 지운다
    // (이미 정산된 지난 주 콘테스트에는 영향 없음 - PostContestService.tallyPreviousWeek()가
    // 결과를 PostContestResult에 스냅샷으로 남긴 뒤라 원본 투표를 지워도 과거 결과는 그대로).
    @Modifying
    @Query("DELETE FROM PostContestVote v WHERE v.voter.id = :userId")
    void deleteAllByVoterId(@Param("userId") Long userId);
}
