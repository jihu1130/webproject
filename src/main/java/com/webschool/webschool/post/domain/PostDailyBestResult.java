package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 일간 추천 게시글 1위 이력(2026-09-16, 기존 PostContestResult를 대체) -
// PostRecommendService.tallyPreviousDay()가 매일 자정 그 전날 추천을 가장 많이 받은 게시글 1개에
// 포인트를 지급할 때 한 행씩 남긴다. post/author는 소프트 삭제만 하는 컨벤션이라(CLAUDE.md) post는
// 항상 유효하지만, author는 2026-09 하드 삭제 도입으로 작성자가 탈퇴 1주일 후 실제 삭제되면
// null이 된다(AccountHardDeleteService 참고).
@Entity
@Table(name = "post_daily_best_results")
@Getter @Setter
@NoArgsConstructor
public class PostDailyBestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate resultDate; // 집계 대상 날짜(그 전날) - 배치가 도는 날짜가 아니라 그 전날 기준

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private int recommendCount;

    @Column(nullable = false)
    private int prizePoints;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
