package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "calendar_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK (기본키)

    @Column(nullable = false)
    private String title; // 일정 제목 (예: 중간고사, 개교기념일)

    @Column(nullable = false)
    private LocalDate startDate; // 시작일

    private LocalDate endDate; // 종료일

    private String color; // 캘린더에 표시될 색상 (예: #FF9800)

    @Column(length = 500)
    private String description; // 일정 상세 설명
}