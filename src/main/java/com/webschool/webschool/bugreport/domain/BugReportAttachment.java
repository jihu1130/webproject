package com.webschool.webschool.bugreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 버그 리포트 첨부 사진/영상 - 최대 5개(BugReportService.MAX_ATTACHMENTS), 리치 에디터 본문 삽입이
// 아니라 PostImage처럼 게시물에 딸린 별도 첨부 목록 방식(사용자 요청 "게시글처럼 따로").
// 저장 자체는 FileUploadService(리치 에디터가 쓰는 것과 동일한 이미지/영상 용량 제한)를 재사용한다.
@Entity
@Table(name = "bug_report_attachments")
@Getter @Setter
@NoArgsConstructor
public class BugReportAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id", nullable = false)
    private BugReport bugReport;

    @Column(nullable = false, length = 255)
    private String url;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 10)
    private String kind; // image | video

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
