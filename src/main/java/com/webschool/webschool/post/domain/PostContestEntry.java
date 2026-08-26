package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 인기 게시글 주간 콘테스트(todo.md 4번 항목) 후보 신청 - 본인 게시물만, 콘테스트 회차(주,
// weekStart=그 주 월요일)당 인당 1개. PostReport의 "(post_id, reporter_id)" 유니크 제약과 동일한
// 아이디어로 "(nominator_id, week_start)" 유니크 제약이 "인당 1개"를 강제한다.
@Entity
@Table(name = "post_contest_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"nominator_id", "week_start"}))
@Getter @Setter
@NoArgsConstructor
public class PostContestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 항상 post.getAuthor()와 동일 - 서비스에서 검증(본인 게시물만 신청 가능). 유니크 제약을
    // 이 컬럼 기준으로 걸기 위해 별도 컬럼으로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nominator_id", nullable = false)
    private User nominator;

    @Column(nullable = false)
    private LocalDate weekStart; // 그 주 월요일 날짜로 정규화(PostContestService.currentWeekStart())

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
