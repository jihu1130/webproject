package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "meals")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    private LocalDate mealDate; // 날짜 (LocalDate)
    private String mealType;    // 급식 종류 (예: 중식)

    @Column(columnDefinition = "TEXT")
    private String menu;        // 급식 식단 내용

    private LocalDateTime updatedAt; // NEIS에서 캐시해온 시각 - 24시간 TTL 판단 기준
}