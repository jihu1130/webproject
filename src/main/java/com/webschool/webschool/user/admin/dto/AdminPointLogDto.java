package com.webschool.webschool.user.admin.dto;

import lombok.Builder;
import lombok.Getter;

// 포인트 관리 사이트 전체 로그 한 줄(관리자 페이지 재구성, 2026-09-21 추가) - 개인용
// UserPointLogDto와 달리 누구의 로그인지 알아야 하므로 username/nickname을 포함한다.
@Getter
@Builder
public class AdminPointLogDto {
    private String username;
    private String nickname;
    private int points;
    private String reason;
    private String createdAt;
}
