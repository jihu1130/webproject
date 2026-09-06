package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_id"}))
@Getter @Setter
@NoArgsConstructor
public class PostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // nullable - 신고자가 탈퇴 1주일 후 하드 삭제되면 null(AccountHardDeleteService 참고).
    // 신고 기록 자체는 남겨서 관리자가 "왜 블라인드됐는지" 계속 확인할 수 있게 한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Column(length = 300)
    private String reason; // 신고 사유 (선택 입력, 관리자 페이지에서 확인용)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
