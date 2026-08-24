package com.webschool.webschool.bugreport.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BugReportDto {
    private Long id;
    private String title;
    private String content;
    private String reporterDisplay; // 로그인 제출: 닉네임, 비로그인: 입력한 이름 또는 "익명"
    private String contactEmail;
    private boolean resolved;
    private String createdAt;
}
