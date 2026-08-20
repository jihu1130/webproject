package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 게시글 좋아요 - 사용자당 게시글 하나에 좋아요 한 번만(unique 제약으로 토글 시 중복 방지),
// PostReport와 동일한 구조. 실제 카운트는 Post.likeCount에 비정규화해서 들고 있고, 이 테이블은
// "이 사용자가 이미 좋아요를 눌렀는지" 판단(토글)에만 쓴다.
@Entity
@Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

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
