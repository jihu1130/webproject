package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 포인트 소비 상점(todo.md 요구사항) - 아직 스캐폴딩 단계. 실제 판매 카탈로그를 채우는 시딩,
// 구매/장착 화면(컨트롤러), UserPointService.spend() 호출부는 전부 미구현 - 이 엔티티는 그 상점이
// 다룰 "판매 아이템" 하나를 표현하는 모델만 미리 잡아둔 것. value를 label과 분리한 이유: 칭호는
// 화면에 보이는 문구(label)와 실제 적용값(value)이 같지만, 색상은 label이 사람이 읽는 이름
// ("골드")이고 value가 실제 CSS 값(예: "#d4af37")이라 둘이 다르다.
@Entity
@Table(name = "shop_items")
@Getter @Setter
@NoArgsConstructor
public class ShopItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 50)
    private String label; // 화면에 보이는 이름 (칭호 문구 또는 색상 이름)

    @Column(nullable = false, length = 20)
    private String value; // 실제 적용값 (칭호는 label과 동일, 색상은 CSS 색상값)

    @Column(nullable = false)
    private int price;

    // 판매 중단된 아이템은 상점 목록에서만 숨긴다 - 이미 구매해서 장착 중인 사용자에게서 뺏지 않는다.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    public enum Type {
        TITLE, AVATAR_COLOR
    }
}
