package com.webschool.webschool.user.admin.dto;
import com.webschool.webschool.user.dto.UserPenaltyDto;
import com.webschool.webschool.user.point.dto.UserPointLogDto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 총관리자가 "계정 관리"에서 특정 계정의 프로필을 확인할 때 쓰는 상세 DTO.
@Getter
@Builder
public class AdminUserProfileDto {
    private Long id;
    private String username;
    private String nickname;
    private String profileImageUrl;
    private String role;
    private String schoolName;
    private String schoolKind;
    private String grade;
    private String classNum;
    private boolean deleted;
    private String deletedAt;
    private boolean active;
    private boolean canManageReports;
    private boolean canManagePosts;
    private boolean canManageScheduleComments;
    private boolean canManageNotices;
    private boolean canManageShop;
    private long postCount;
    private long commentCount;
    private List<AdminUserProfilePostDto> recentPosts;
    private List<AdminUserProfileCommentDto> recentComments;
    private List<UserPenaltyDto> penalties;
    private String equippedTitle;
    private String equippedAvatarColor;
    private String equippedEffect;
    // 포인트 관리(관리자 페이지 재구성, 2026-09-21 추가) - 계정 프로필에서 바로 포인트 현황을 보고
    // 지급/차감할 수 있게 확장.
    private int currentPoints;
    private String tierLabel;
    private List<UserPointLogDto> recentPointLogs;
}
