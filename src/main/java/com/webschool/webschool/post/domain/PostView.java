package com.webschool.webschool.post.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 조회수 중복 방지를 세션(브라우저별)뿐 아니라 IP 기준으로도 검사하기 위한 조회 이력 -
// 세션이 만료되거나 새 브라우저/시크릿창으로 우회해도, 같은 IP가 최근에 이미 본 글이면
// 조회수를 다시 올리지 않는다(PostViewService.shouldCountView() 참고). 로그인 여부와
// 무관하게 기록하므로 User 연관관계는 없다.
@Entity
@Table(name = "post_views")
@Getter @Setter
@NoArgsConstructor
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(nullable = false)
    private LocalDateTime viewedAt;

    @PrePersist
    public void prePersist() {
        this.viewedAt = LocalDateTime.now();
    }
}
