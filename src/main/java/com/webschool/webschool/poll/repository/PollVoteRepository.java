package com.webschool.webschool.poll.repository;

import com.webschool.webschool.poll.domain.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    List<PollVote> findByOption_Poll_Id(Long pollId);

    // 단일/복수선택 모두 "새로 고른 것으로 교체" 방식이라, 새로 투표하기 전에 이 사용자의 기존 투표를
    // 전부 지우는 용도(PollService.vote() 참고 - QnA 답변 채택의 "새로 채택하면 기존 채택 자동 해제"와
    // 동일한 원자적 교체 패턴).
    List<PollVote> findByOption_Poll_IdAndVoter_Id(Long pollId, Long voterId);

    // 관리자 설문 목록 화면에서 설문마다 참여자 수를 보여주기 위한 카운트(PollService.buildResult()의
    // totalVoters와 동일한 정의 - 옵션별 투표수 합이 아니라 "투표한 사람 수"이므로 distinct).
    @Query("select count(distinct v.voter.id) from PollVote v where v.option.poll.id = :pollId")
    long countDistinctVotersByPollId(Long pollId);
}
