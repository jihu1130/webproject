package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserSummaryDto {
    private Long id;
    private String username;
    private String nickname;
    private String role; // ROLE_USER / ROLE_ADMIN(부관리자) / ROLE_SUPER_ADMIN(총관리자)
    private String schoolName;
    private boolean deleted;
    private String deletedAt; // 탈퇴하지 않았으면 null
    private boolean active; // false면 총관리자가 비활성화(정지)한 계정
    // 부관리자에게 부여된 권한 - ROLE_ADMIN에게만 의미 있음(ROLE_SUPER_ADMIN은 값과 무관하게 항상 전체 허용)
    private boolean canManageReports;
    private boolean canManagePosts;
    private boolean canManageScheduleComments;
    private boolean canManageNotices;
    private boolean canManageUsers;
    private boolean canManageAdminPermissions;
    private boolean canViewAuditLog;
}
