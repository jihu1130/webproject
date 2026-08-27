package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 상점 화면(/shop) 전용 - ShopItem.Type이 TITLE/AVATAR_COLOR 둘뿐이라 템플릿이 바로 순회할 수
// 있게 두 리스트로 미리 나눠서 내려준다.
@Getter
@Builder
public class ShopCatalogDto {
    private List<ShopItemDto> titles;
    private List<ShopItemDto> colors;
}
