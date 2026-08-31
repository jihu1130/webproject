package com.webschool.webschool.poll.domain;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 게시글/오늘의 한마디에 첨부하는 범용 설문. Post와 ScheduleComment 양쪽에 붙을 수 있어 post 패키지
// 전용이 아니라 최상위 패키지로 분리했다(notice가 post와 분리된 것과 같은 판단, CLAUDE.md 참고).
// post/scheduleComment 중 정확히 하나만 채워진다 - DB 제약 대신 PollService에서 검증한다(이
// 코드베이스는 이런 "정확히 하나만" 류의 비즈니스 규칙을 서비스 레이어에서 검증하는 컨벤션을 따른다).
@Entity
@Table(name = "polls")
@Getter @Setter
@NoArgsConstructor
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_comment_id")
    private ScheduleComment scheduleComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 200)
    private String question;

    // 복수 선택 허용 여부 - 설문 만들 때 작성자가 설정
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean allowMultiple;

    // "기타" - 투표자가 주어진 옵션 외에 직접 새 옵션을 추가할 수 있는지
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean allowCustomOption;

    // 익명 투표 - 결과에서 누가 어느 옵션에 투표했는지 숨김. 공개범위(visibilityScope)와는 독립적인
    // 별개 옵션(둘 다 킬 수도, 하나만 킬 수도 있음 - 사용자 확정 요구사항).
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean anonymous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'SAME_CLASS'")
    private VisibilityScope visibilityScope = VisibilityScope.SAME_CLASS;

    // visibilityScope=SAME_GRADE일 때만 의미 있음 - true면 같은 학교 안에서만, false면 학교 무관하게
    // 같은 학년 전체(사용자 요구사항: "같은학년(같은 학교인지 아닌지 선택)")
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean sameSchoolOnly = true;

    // 마감 기한(선택) - null이면 마감 없이 계속 투표 가능. 지나면 PollService.vote()가 거부한다.
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    // 소프트 딜리트(이 코드베이스의 삭제 전 규칙) - 한마디 수정 화면에서 작성자가 설문을 끌 때 씀
    // (PollService.deletePollForComment()). 투표 기록(PollVote)은 그대로 남지만 조회/투표 경로에서
    // 전부 이 플래그로 걸러진다.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.deleted = false;
    }

    public enum VisibilityScope {
        SAME_CLASS, SAME_GRADE, PUBLIC_LINK
    }
}
