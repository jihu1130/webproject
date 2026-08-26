package com.webschool.webschool.poll.repository;

import com.webschool.webschool.poll.domain.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    List<PollOption> findByPoll_IdOrderByIdAsc(Long pollId);
}
