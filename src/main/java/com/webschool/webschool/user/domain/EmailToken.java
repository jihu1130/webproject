package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 이메일 인증 링크 / 비밀번호 재설정 링크에 쓰는 1회용 토큰. 두 용도가 발급/소비 로직(만료 체크,
// 사용 여부 체크)이 동일해서 별도 엔티티로 쪼개지 않고 purpose로만 구분한다.
@Entity
@Table(name = "email_tokens")
@Getter @Setter
@NoArgsConstructor
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean used;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public enum Purpose {
        VERIFY_EMAIL, RESET_PASSWORD
    }
}
