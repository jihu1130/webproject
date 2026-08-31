package com.webschool.webschool.bugreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 문의(BugReport) 답변 스레드 - PostComment와 동일한 소프트 삭제 컨벤션. 여러 번 답변할 수 있게
// 댓글처럼 여러 행으로 쌓는다(문의 방식 결정: 1:1 채팅형 대신 스레드형 - CLAUDE.md/작업 지시서 참고,
// 이 앱에 실시간 인프라가 없고 비로그인 문의자는 세션이 없어 "채팅"이 성립하지 않기 때문).
@Entity
@Table(name = "inquiry_replies")
@Getter @Setter
@NoArgsConstructor
public class InquiryReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id", nullable = false)
    private BugReport bugReport;

    @Column(nullable = false, length = 50)
    private String adminUsername; // 답변 작성 관리자 - User FK 대신 username만(탈퇴해도 이력은 남아야 함)

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.deleted = false;
    }
}
