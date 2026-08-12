package com.webschool.webschool.school.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_comments")
@Getter @Setter
@NoArgsConstructor
public class ScheduleComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false)
    private LocalDate targetDate; // 댓글이 달린 날짜

    @Column(nullable = false)
    private String grade; // 같은 학년끼리만 공유

    @Column(nullable = false)
    private String classNm; // 같은 반끼리만 공유

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted; // 소프트 딜리트 - 작성자 본인/관리자 삭제 시 true (물리적으로는 남아있음, PostComment와 동일 패턴)

    private LocalDateTime deletedAt; // 삭제된 경우에만 값이 채워짐

    @Column(nullable = false)
    private int reportCount; // 서로 다른 사용자 3명이 신고하면 blind=true (PostComment/CommentReport와 동일 패턴)

    @Column(nullable = false, columnDefinition = "int default 0")
    private int likeCount; // 좋아요 수 (ScheduleCommentLike 테이블에 비정규화)

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blind; // 신고 누적으로 자동 블라인드 처리된 한마디인지 여부

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부 - 내용이 수정되면 자동으로 false로 리셋됨

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt; // 수정된 경우에만 값이 채워짐

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.deleted = false;
        this.reportCount = 0;
        this.likeCount = 0;
        this.blind = false;
        this.reportCleared = false;
    }
}
