package com.webschool.webschool.bugreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 문의(BugReport) 답변 스레드 - PostComment와 동일한 소프트 삭제 컨벤션. 여러 번 답변할 수 있게
// 댓글처럼 여러 행으로 쌓는다(문의 방식 결정: 1:1 채팅형 대신 스레드형 - CLAUDE.md/작업 지시서 참고,
// 이 앱에 실시간 인프라가 없고 비로그인 문의자는 세션이 없어 "채팅"이 성립하지 않기 때문).
// 원래는 관리자→제출자 단방향(제출자가 재답장할 방법이 없다는 사용자 지적으로 확장)이었는데,
// 로그인한 제출자는 본인 문의에 한해 재답장할 수 있게 됐다(익명 제출은 여전히 세션이 없어
// 이메일 전용 - 기존 설계 취지 유지). adminUsername/userUsername 중 정확히 하나만 채워지고,
// 어느 쪽이 채워졌는지로 작성자를 구분한다(별도 enum/boolean 없이 - InquiryReplyDto.isFromAdmin()).
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

    @Column(length = 50)
    private String adminUsername; // 답변 작성 관리자 - User FK 대신 username만(탈퇴해도 이력은 남아야 함)

    @Column(length = 50)
    private String userUsername; // 제출자 본인이 단 재답장일 때만 채워짐(adminUsername과 배타적)

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
