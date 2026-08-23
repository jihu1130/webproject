package com.webschool.webschool.user.dto;

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
    private long postCount;
    private long commentCount;
    private List<AdminUserProfilePostDto> recentPosts;
    private List<AdminUserProfileCommentDto> recentComments;
    private List<UserPenaltyDto> penalties;
}
