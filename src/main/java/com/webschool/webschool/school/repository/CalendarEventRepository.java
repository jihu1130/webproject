package com.webschool.webschool.school.repository;

import com.webschool.webschool.school.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // 특정 기간(시작일~종료일) 사이의 일정을 조회하는 커스텀 메서드
    List<CalendarEvent> findByStartDateBetween(LocalDate start, LocalDate end);
}