package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_penalties")
@Getter @Setter
@NoArgsConstructor
public class UserPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id", nullable = false)
    private User issuedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt; // null = 영구

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean revoked; // 총관리자의 조기 해제

    private LocalDateTime revokedAt;

    @PrePersist
    public void prePersist() {
        this.issuedAt = LocalDateTime.now();
    }

    // 지금 시점 기준으로 이 제재가 유효한지 - User에 별도 상태 필드를 두지 않고 매번 이 계산으로
    // 판단한다(방학 D-Day와 동일한 "읽는 시점에 계산" 방식). 만료되면 별도 처리 없이 자동으로 false.
    public boolean isCurrentlyActive() {
        if (revoked) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(LocalDateTime.now());
    }

    public enum Type {
        WARNING("경고"),
        POST_SUSPENSION("게시글 작성정지"),
        COMMUNITY_SUSPENSION("커뮤니티 임시차단"),
        DEACTIVATION("계정 비활성화");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
