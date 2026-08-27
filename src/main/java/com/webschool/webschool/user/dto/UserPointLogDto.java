package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPointLogDto {
    private int points; // 양수=적립, 음수=차감/소비
    private String reason;
    private String createdAt;
}
