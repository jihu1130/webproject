package com.webschool.webschool.post.repository;

import com.webschool.webschool.post.domain.PostContestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostContestResultRepository extends JpaRepository<PostContestResult, Long> {
    List<PostContestResult> findAllByOrderByWeekStartDescRankAsc();
}
