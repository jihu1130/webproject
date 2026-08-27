package com.webschool.webschool.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 상점 구매 이력(todo.md "포인트 소비" 요구사항) - "누가 어떤 ShopItem을 샀는지"만 담는다.
// (user_id, shop_item_id) 유니크로 같은 아이템 중복 구매를 막는다. 한번 사면 계속 보유하며
// 무료로 장착/해제를 전환할 수 있고(ShopService.equip()/unequip()), 실제 장착 상태는 여기가
// 아니라 User.equippedTitle/equippedAvatarColor에 별도로 저장한다(보유와 장착은 다른 개념 -
// 여러 개를 가지고 있어도 장착은 타입당 하나뿐이라서).
@Entity
@Table(name = "user_shop_items", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "shop_item_id"}))
@Getter @Setter
@NoArgsConstructor
public class UserShopItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_item_id", nullable = false)
    private ShopItem shopItem;

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @PrePersist
    public void prePersist() {
        this.purchasedAt = LocalDateTime.now();
    }
}
