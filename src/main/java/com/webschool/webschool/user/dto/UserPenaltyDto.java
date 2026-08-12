package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPenaltyDto {
    private Long id;
    private String type;
    private String typeLabel;
    private String reason;
    private String issuedByNickname;
    private String issuedAt;
    private String expiresAt; // null이면 영구
    private boolean revoked;
    private boolean active; // 지금 시점 기준 유효 여부(적용중/만료/해제됨 표시용)
}
