package com.webschool.webschool.poll.repository;

import com.webschool.webschool.poll.domain.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    List<PollOption> findByPoll_IdOrderByIdAsc(Long pollId);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 옵션은 남기고 추가자 참조만 끊는다.
    @Modifying
    @Query("UPDATE PollOption o SET o.addedBy = null WHERE o.addedBy.id = :userId")
    void detachAddedBy(@Param("userId") Long userId);
}
