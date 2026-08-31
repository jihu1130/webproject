package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShopItemDto {
    private Long id;
    private String type; // ShopItem.Type.name()
    private String label;
    private String value;
    private String effect; // ShopItem.Effect.name() - AVATAR_COLOR 종류에서만 의미 있음
    private int price;
    private boolean active;
    private boolean owned;    // 상점 화면 전용 - 관리자 카탈로그 조회에는 항상 false
    private boolean equipped; // 상점 화면 전용
}
