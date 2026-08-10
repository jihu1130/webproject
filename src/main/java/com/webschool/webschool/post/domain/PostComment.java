package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_comments")
@Getter @Setter
@NoArgsConstructor
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted; // 소프트 딜리트 - 작성자 본인 삭제 시 true (물리적으로는 남아있음)

    private LocalDateTime deletedAt; // 삭제된 경우에만 값이 채워짐

    @Column(nullable = false)
    private int reportCount; // 서로 다른 사용자 3명이 신고하면 blind=true (PostReport와 동일 패턴)

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blind; // 신고 누적으로 자동 블라인드 처리된 댓글인지 여부

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부 - 댓글이 수정되면 자동으로 false로 리셋됨

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt; // 수정된 경우에만 값이 채워짐

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.deleted = false;
        this.reportCount = 0;
        this.blind = false;
        this.reportCleared = false;
    }
}
