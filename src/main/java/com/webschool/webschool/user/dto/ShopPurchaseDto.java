package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 상점 구매 내역 한 줄(관리자 화면 전용, 관리자 페이지 재구성 2026-09-21 추가) - AdminShopItemController의
// "구매 내역" 서브탭에서 씀. ShopItemDto와 마찬가지로 user.dto에 둔다(ShopService가 관리자
// 카탈로그 CRUD와 사용자 구매를 한 서비스에서 같이 다루는 기존 관례를 그대로 따름).
@Getter
@Builder
public class ShopPurchaseDto {
    private String buyerUsername;
    private String buyerNickname;
    private String itemLabel;
    private String itemType;
    private int price;
    private LocalDateTime purchasedAt;
}
