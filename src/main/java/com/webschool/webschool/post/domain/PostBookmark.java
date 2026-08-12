package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 게시글 북마크 - PostLike와 동일한 토글 구조. 좋아요와 달리 개수를 공개적으로 보여주지 않고
// (다른 사람 눈에 보이는 정보가 아니라 "내가 나중에 다시 보려고 저장"하는 개인용 기능이라
// PostBookmark 자체가 목록(마이페이지 "북마크" 탭)의 근거 테이블이 된다 - 카운트 비정규화 없음.
@Entity
@Table(name = "post_bookmarks", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
public class PostBookmark {

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
