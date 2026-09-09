package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.PersonalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersonalEventRepository extends JpaRepository<PersonalEvent, Long> {
    List<PersonalEvent> findByUser_IdAndEventDateOrderByCreatedAtAsc(Long userId, LocalDate eventDate);

    // 수정/삭제 시 소유권 검증까지 한 번에 - PersonalEventService에서 본인 소유가 아니면 empty가 반환된다.
    Optional<PersonalEvent> findByIdAndUser_Id(Long id, Long userId);

    // 캘린더 월 그리드용 - 날짜마다 따로 조회하지 않고(N+1 방지) 보이는 범위 전체를 한 번에 조회해서
    // 일정이 있는 날짜만 점으로 표시한다(SchoolService.getMonthlyEvents()와 동일한 이유).
    @Query("SELECT DISTINCT p.eventDate FROM PersonalEvent p WHERE p.user.id = :userId AND p.eventDate BETWEEN :start AND :end")
    List<LocalDate> findDistinctDatesByUserIdAndEventDateBetween(@Param("userId") Long userId,
                                                                  @Param("start") LocalDate start,
                                                                  @Param("end") LocalDate end);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 개인 일정은 소유자 없이는 의미 없는 순수 개인
    // 데이터라 ScheduleComment처럼 FK만 끊는 게 아니라 행 자체를 함께 삭제한다(B그룹).
    @Modifying
    @Query("DELETE FROM PersonalEvent p WHERE p.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
