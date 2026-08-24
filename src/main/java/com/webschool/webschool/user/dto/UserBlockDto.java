package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserBlockDto {
    private Long id;
    private Long userId;
    private String nickname;
    private String createdAt;
    private String expiresAt; // null이면 영구
    private boolean permanent;
    private boolean userDeleted; // 차단한 뒤 상대가 탈퇴하면 프로필 링크를 걸지 않기 위한 플래그
}
