package com.webschool.webschool.post.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 게시글 추천(2026-09-16, 기존 "인기 게시글 주간 콘테스트"의 본인 후보 신청 방식을 대체) - 자유
// 게시판(FREE) + 전체 공개(PUBLIC) 게시글은 별도 신청 없이 자동으로 추천 대상이 되고, 다른
// 사용자가 이 글에 "추천"을 누르면 한 행씩 쌓인다(PostRecommendService.recommend() 참고). 일간/
// 주간/월간/전체 랭킹은 전부 이 테이블을 createdAt 기준 기간으로 집계해서 즉석에서 계산하고(별도
// 리셋 로직 없음 - 기간이 지나면 그 기간에 해당하는 행이 자연히 없어질 뿐), 취소는 지원하지
// 않는다(좋아요와 달리 순위 조작 방지 목적의 1회성 의사표시로 설계). "(post_id, voter_id)" 유니크
// 제약으로 한 사람이 같은 글에 중복 추천하는 것만 막는다.
@Entity
@Table(name = "post_recommends", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "voter_id"}))
@Getter @Setter
@NoArgsConstructor
public class PostRecommend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // null일 때만 현재 시각으로 채운다(기본 동작) - TestDataSeeder가 일간/주간/월간 랭킹이 서로
    // 다르게 보이는 데모 데이터를 만들 때 과거 날짜를 미리 세팅해서 넘기면 그 값을 그대로 쓴다.
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
