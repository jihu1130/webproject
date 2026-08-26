package com.webschool.webschool.poll.repository;

import com.webschool.webschool.poll.domain.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    // 게시글/한마디 하나에 설문은 최대 1개만 붙일 수 있다(작성 화면에서 설문 첨부는 한 번뿐).
    Optional<Poll> findByPost_Id(Long postId);
    Optional<Poll> findByScheduleComment_Id(Long scheduleCommentId);
}
