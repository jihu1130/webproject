package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 포인트 적립 이력 - append-only. "왜 포인트를 받았는지" 사람이 읽을 수 있는 근거를 남기고,
// UserPointService의 일일 획득 한도(어뷰징 방지) 계산 근거로도 쓰인다(오늘 적립분 합계 조회).
// AdminActionLog와 동일하게 대상(User)을 FK로 직접 참조하지 않는 대신, 여기는 소유자가 명확하고
// User가 삭제돼도(소프트 딜리트) 남아있어야 하는 이력이라 실제 FK(@ManyToOne)로 둔다.
@Entity
@Table(name = "user_point_logs")
@Getter @Setter
@NoArgsConstructor
public class UserPointLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int points; // 이번에 실제로 적립된 양(일일 한도에 걸려 요청보다 적게 적립됐을 수 있음)

    @Column(nullable = false, length = 50)
    private String reason; // 사람이 읽을 적립 사유 (예: "게시글 작성", "답변 채택됨")

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
