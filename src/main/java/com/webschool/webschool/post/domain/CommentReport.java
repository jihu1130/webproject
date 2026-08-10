package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// PostReport와 동일한 패턴 (comment_id + reporter_id 유니크로 중복 신고 방지)
@Entity
@Table(name = "comment_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "reporter_id"}))
@Getter @Setter
@NoArgsConstructor
public class CommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private PostComment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(length = 300)
    private String reason; // 신고 사유 (선택 입력)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
