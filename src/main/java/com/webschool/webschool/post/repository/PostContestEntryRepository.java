package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostContestEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PostContestEntryRepository extends JpaRepository<PostContestEntry, Long> {
    List<PostContestEntry> findByWeekStartOrderByIdAsc(LocalDate weekStart);

    boolean existsByNominator_IdAndWeekStart(Long nominatorId, LocalDate weekStart);

    Optional<PostContestEntry> findByPost_IdAndWeekStart(Long postId, LocalDate weekStart);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 후보 신청 기록은 개인 활동 흔적이라 함께 지운다
    // (PostContestVoteRepository.deleteAllByVoterId()와 동일한 이유로 정산된 과거 결과엔 영향 없음).
    @Modifying
    @Query("DELETE FROM PostContestEntry e WHERE e.nominator.id = :userId")
    void deleteAllByNominatorId(@Param("userId") Long userId);
}
