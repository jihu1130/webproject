package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 인기 게시글 주간 콘테스트 투표 - 한 주에 후보 전체를 통틀어 1표만(누구에게 투표했는지와 무관하게).
// "(voter_id, week_start)" 유니크 제약으로 강제 - weekStart는 entry.weekStart와 항상 같은 값이지만
// 이 제약을 걸기 위해 비정규화해서 이 테이블에도 들고 있다.
@Entity
@Table(name = "post_contest_votes", uniqueConstraints = @UniqueConstraint(columnNames = {"voter_id", "week_start"}))
@Getter @Setter
@NoArgsConstructor
public class PostContestVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private PostContestEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
