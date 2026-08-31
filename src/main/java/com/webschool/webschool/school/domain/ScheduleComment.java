package com.webschool.webschool.school.domain;

import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// @DynamicUpdate - Post.java와 동일한 이유(likeCount/reportCount 원자적 벌크 UPDATE를 다른 필드
// 변경이 덮어쓰지 않도록).
@Entity
@Table(name = "schedule_comments")
@DynamicUpdate
@Getter @Setter
@NoArgsConstructor
public class ScheduleComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 내부 PK - FK/관리자 화면에서만 쓰고 공개 URL에는 노출하지 않음(Post.id와 동일 패턴)

    // nullable인 이유는 Post.uuid와 동일 - 기존 행이 있는 테이블에 NOT NULL로 추가하면
    // ddl-auto=update가 실패한다. 신규 한마디는 prePersist()가 채우고, 기존 행은 배포 후
    // 1회 SQL로 백필한다.
    @Column(unique = true, length = 36)
    private String uuid; // 공개 URL(/school/comments/{uuid})에 쓰는 값

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

    // 리치 에디터(Quill) 산출물 - 저장 전 ScheduleCommentService가 HtmlSanitizer로 정제한 안전한
    // HTML만 들어간다(Post.content와 동일 패턴). 사진/동영상/파일 삽입을 지원하면서 300자 제한이던
    // 예전 "한줄 댓글" 컬럼으로는 부족해져서 MEDIUMTEXT로 확장.
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
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
        this.uuid = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.deleted = false;
        this.reportCount = 0;
        this.likeCount = 0;
        this.blind = false;
        this.reportCleared = false;
    }
}
