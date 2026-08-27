package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 출석체크 이력(todo.md 요구사항) - 하루 한 번만 지급되도록 (user_id, attendance_date) 유니크
// 제약으로 중복 체크인을 막는다. UserPointLog와 별개 테이블인 이유: UserPointLog는 "왜 포인트를
// 받았는지"의 범용 로그라 오늘 이미 출석했는지 빠르게 조회하기엔 맞지 않고(reason 문자열 매칭이
// 필요해짐), 이 테이블은 "이 날짜에 출석했는가"만 보는 존재 여부 조회 전용이다.
@Entity
@Table(name = "attendance_logs", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "attendance_date"}))
@Getter @Setter
@NoArgsConstructor
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
