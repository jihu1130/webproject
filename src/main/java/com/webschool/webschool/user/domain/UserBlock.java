package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Getter @Setter
@NoArgsConstructor
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt; // null = 영구

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isCurrentlyActive() {
        return expiresAt == null || expiresAt.isAfter(LocalDateTime.now());
    }
}
