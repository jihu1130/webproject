package com.webschool.webschool.notice.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 공지사항 - 일반 게시글(Post)과 완전히 분리된 별도 모델. "활성 공지는 항상 정확히 1개"라는 제약을
// DB 제약이 아니라 NoticeService.create()에서 새 공지를 만들 때마다 기존 활성 공지를 먼저 비활성화하는
// 방식으로 보장한다(동시에 여러 관리자가 작성해도 트랜잭션 안에서 순차 처리되므로 안전). 비활성(과거)
// 공지는 삭제하지 않고 이력으로 계속 보관한다(todo.md 요구사항).
@Entity
@Table(name = "notices")
@Getter @Setter
@NoArgsConstructor
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
