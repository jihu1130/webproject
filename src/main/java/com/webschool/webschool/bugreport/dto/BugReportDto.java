package com.webschool.webschool.bugreport.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BugReportDto {
    private Long id;
    private String category; // BugReport.Category.name()
    private String categoryLabel;
    private String title;
    private String content;
    private String reporterDisplay; // 로그인 제출: 닉네임, 비로그인: 입력한 이름 또는 "익명"
    private Long reporterId; // null이면 비로그인 제출 - 관리자 화면에서 프로필 링크 노출 여부 판단
    private String contactEmail;
    private boolean resolved;
    private String createdAt;
    private List<BugReportAttachmentDto> attachments;
    private List<InquiryReplyDto> replies;
}
