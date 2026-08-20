package com.webschool.webschool.school.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// post.domain.CommentReport와 동일한 패턴 (schedule_comment_id + reporter_id 유니크로 중복 신고 방지)
@Entity
@Table(name = "schedule_comment_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_comment_id", "reporter_id"}))
@Getter @Setter
@NoArgsConstructor
public class ScheduleCommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_comment_id", nullable = false)
    private ScheduleComment comment;

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
