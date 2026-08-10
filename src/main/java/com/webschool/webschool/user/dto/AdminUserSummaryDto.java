package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserSummaryDto {
    private Long id;
    private String username;
    private String nickname;
    private String role; // ROLE_USER / ROLE_ADMIN
    private String schoolName;
    private boolean deleted;
    private String deletedAt; // 탈퇴하지 않았으면 null
}
