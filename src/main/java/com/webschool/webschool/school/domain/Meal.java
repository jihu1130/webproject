package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

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
}