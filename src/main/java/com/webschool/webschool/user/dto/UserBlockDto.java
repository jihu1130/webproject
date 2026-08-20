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
}
