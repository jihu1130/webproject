package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminReportItemDto {
    private String reporterNickname;
    private String reporterUsername;
    private String reason; // 미입력 시 null
    private String createdAt;
}
