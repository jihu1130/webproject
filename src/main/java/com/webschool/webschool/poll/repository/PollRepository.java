package com.webschool.webschool.poll.repository;

import com.webschool.webschool.poll.domain.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    // 게시글/한마디 하나에 설문은 최대 1개만 붙일 수 있다(작성 화면에서 설문 첨부는 한 번뿐).
    // 소프트 삭제된 설문(작성자가 한마디 수정 화면에서 끈 경우)은 조회에서 제외한다.
    Optional<Poll> findByPost_IdAndDeletedFalse(Long postId);
    Optional<Poll> findByScheduleComment_IdAndDeletedFalse(Long scheduleCommentId);

    // 관리자 설문 관리 화면의 "전체"/"삭제됨" 탭 (AdminPostService.getAllPosts()/getDeletedPosts()와 동일 패턴)
    List<Poll> findAllByDeletedFalseOrderByCreatedAtDesc();
    List<Poll> findAllByDeletedTrueOrderByDeletedAtDesc();

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 설문은 남기고 작성자 참조만 끊는다.
    @Modifying
    @Query("UPDATE Poll p SET p.creator = null WHERE p.creator.id = :userId")
    void detachCreator(@Param("userId") Long userId);
}
