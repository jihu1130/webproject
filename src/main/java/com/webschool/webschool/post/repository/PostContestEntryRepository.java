package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostContestEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PostContestEntryRepository extends JpaRepository<PostContestEntry, Long> {
    List<PostContestEntry> findByWeekStartOrderByIdAsc(LocalDate weekStart);

    boolean existsByNominator_IdAndWeekStart(Long nominatorId, LocalDate weekStart);
}
