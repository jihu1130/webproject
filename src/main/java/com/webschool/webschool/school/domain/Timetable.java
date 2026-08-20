package com.webschool.webschool.school.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

// 버그 수정: unique 제약이 없어서, 같은 반 학생 두 명이 아직 캐시 안 된 날짜를 거의 동시에 열람하면
// 둘 다 캐시 미스 -> 둘 다 NEIS 조회 -> 둘 다 저장돼서 같은 교시가 중복 저장될 수 있었다
// (PostReport 등이 쓰는 unique 제약 패턴과 동일하게 추가, 방어 코드는 SchoolService 참고).
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
}