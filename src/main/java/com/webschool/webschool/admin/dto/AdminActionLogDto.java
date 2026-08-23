package com.webschool.webschool.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminActionLogDto {
    private Long id;
    private String adminUsername;
    private Long adminUserId;
    private String targetType;
    private String targetTypeLabel;
    private Long targetId;
    private String targetUrl;
    private String action;
    private String actionLabel;
    private String actionBadgeClass;
    private String detail;
    private String createdAt;
}
