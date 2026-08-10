package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// AdminUserProfileDto 안에 들어가는 "최근 작성 게시글" 요약 (관리자 게시글 상세로 바로 이동하는 링크용)
@Getter
@Builder
public class AdminUserProfilePostDto {
    private Long id;
    private String title;
    private String categoryLabel;
    private String createdAt;
}
