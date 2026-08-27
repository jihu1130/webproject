package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 인기 게시글 주간 콘테스트 과거 우승 이력(todo.md "콘테스트/설문 후속" 항목) - 매주 월요일 자정
// PostContestService.tallyPreviousWeek()이 그 주 상위 1~3위를 확정할 때 한 행씩 남긴다. 이 기록이
// 생기기 전에는 우승 데이터가 UserPointLog.reason 텍스트와 Notification에만 남아있어 별도 조회
// 화면을 만들 수 없었다. post/author는 소프트 삭제만 하는 컨벤션이라(CLAUDE.md) FK가 항상 유효.
@Entity
@Table(name = "post_contest_results")
@Getter @Setter
@NoArgsConstructor
public class PostContestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate weekStart;

    // MySQL 8부터 RANK가 예약어라 컬럼명을 그대로 쓰면 CREATE TABLE이 조용히 실패한다
    // (CLAUDE.md "알려진 함정" - 예약어 컬럼명 문제, 실제로 이 테이블에서 겪음).
    @Column(name = "rank_position", nullable = false)
    private int rank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private int voteCount;

    @Column(nullable = false)
    private int prizePoints;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
