package com.webschool.webschool.school.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 오늘의 한마디(ScheduleComment) 북마크 - post.domain.PostBookmark와 동일한 패턴.
@Entity
@Table(name = "schedule_comment_bookmarks", uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
public class ScheduleCommentBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private ScheduleComment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
