package com.webschool.webschool.poll.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// PostLike와 동일한 "대상+사용자" 유니크 제약 패턴 - 한 사용자가 같은 옵션에 중복 투표하는 것만
// 막는다(복수선택 허용 시 옵션마다 별도 행이 쌓이는 방식으로 복수선택을 표현). 익명 설문이어도
// voter는 항상 저장한다(중복투표 방지에 필요) - 화면에 안 보여주는 건 PollService가 결과 DTO로
// 변환하는 시점에 리댁션하는 것으로 처리한다(익명 게시글 닉네임 치환과 동일한 "서비스 레벨
// 리댁션" 패턴, DB에 null을 넣지 않음).
@Entity
@Table(name = "poll_votes", uniqueConstraints = @UniqueConstraint(columnNames = {"poll_option_id", "voter_id"}))
@Getter @Setter
@NoArgsConstructor
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_option_id", nullable = false)
    private PollOption option;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
