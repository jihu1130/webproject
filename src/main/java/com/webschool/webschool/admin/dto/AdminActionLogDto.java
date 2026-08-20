package com.webschool.webschool.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminActionLogDto {
    private Long id;
    private String adminUsername;
    private String targetType;
    private String targetTypeLabel;
    private Long targetId;
    private String action;
    private String actionLabel;
    private String detail;
    private String createdAt;
}
