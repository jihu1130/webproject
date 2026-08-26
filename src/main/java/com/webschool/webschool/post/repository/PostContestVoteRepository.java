package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostContestVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PostContestVoteRepository extends JpaRepository<PostContestVote, Long> {
    List<PostContestVote> findByWeekStart(LocalDate weekStart);

    List<PostContestVote> findByEntry_Id(Long entryId);

    boolean existsByVoter_IdAndWeekStart(Long voterId, LocalDate weekStart);
}
