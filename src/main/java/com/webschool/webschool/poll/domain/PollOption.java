package com.webschool.webschool.poll.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "poll_options")
@Getter @Setter
@NoArgsConstructor
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(nullable = false, length = 100)
    private String label;

    // 작성자가 설문 만들 때부터 넣은 옵션이 아니라, 투표자가 "기타"로 나중에 추가한 옵션인지
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean custom;

    // custom=true일 때만 채워짐 - 누가 이 옵션을 추가했는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_id")
    private User addedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
