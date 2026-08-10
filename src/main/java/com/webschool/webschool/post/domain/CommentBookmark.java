package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 댓글(PostComment) 북마크 - PostBookmark와 동일한 패턴.
@Entity
@Table(name = "comment_bookmarks", uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
public class CommentBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private PostComment comment;

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
