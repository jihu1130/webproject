package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "timetables", uniqueConstraints = @UniqueConstraint(
        columnNames = {"school_id", "class_date", "grade", "class_nm", "period"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    private Integer grade;       // 학년 (Integer)
    private String classNm;      // 반 (String)
    private LocalDate classDate; // 수업 날짜 (LocalDate)
    private Integer period;      // 교시 (Integer)
    private String subject;      // 과목명

    private LocalDateTime updatedAt; // NEIS에서 캐시해온 시각 - 24시간 TTL 판단 기준
}