package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.ShopItem;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserShopItem;
import com.webschool.webschool.user.dto.ShopCatalogDto;
import com.webschool.webschool.user.dto.ShopItemDto;
import com.webschool.webschool.user.repository.ShopItemRepository;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.repository.UserShopItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 포인트 소비 상점(todo.md "포인트 소비" 요구사항) - 카탈로그(ShopItem) CRUD는 관리자가, 구매/장착은
// 사용자가 다루지만 NoticeService가 관리자 CRUD와 사용자 조회를 한 서비스에 같이 두는 것과 동일한
// 이유(같은 자원을 다루는 서비스를 굳이 쪼개지 않는 관례)로 한 서비스에 모았다.
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final UserShopItemRepository userShopItemRepository;
    private final UserRepository userRepository;
    private final UserPointService userPointService;

    // ----- 관리자 카탈로그 CRUD -----

    public List<ShopItemDto> getAllItems() {
        return shopItemRepository.findAll().stream()
                .sorted((a, b) -> a.getType() == b.getType()
                        ? Integer.compare(a.getPrice(), b.getPrice())
                        : a.getType().compareTo(b.getType()))
                .map(item -> toDto(item, false, false))
                .collect(Collectors.toList());
    }

    @Transactional
    public void createItem(ShopItem.Type type, String label, String value, int price) {
        validate(label, value, price);
        ShopItem item = new ShopItem();
        item.setType(type);
        item.setLabel(label.trim());
        item.setValue(value.trim());
        item.setPrice(price);
        item.setActive(true);
        shopItemRepository.save(item);
    }

    @Transactional
    public void updateItem(Long id, String label, String value, int price) {
        validate(label, value, price);
        ShopItem item = shopItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        item.setLabel(label.trim());
        item.setValue(value.trim());
        item.setPrice(price);
    }

    // 하드 삭제 없음(CLAUDE.md "알려진 함정" - 이미 구매한 사용자가 있으면 UserShopItem의 FK가
    // 깨진다) - 판매중지만 지원한다. 이미 구매/장착한 사용자에게는 영향이 없다.
    @Transactional
    public void setActive(Long id, boolean active) {
        ShopItem item = shopItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        item.setActive(active);
    }

    private void validate(String label, String value, int price) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("적용값을 입력해주세요.");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("가격은 1 이상이어야 합니다.");
        }
    }

    // ----- 사용자 조회/구매/장착 -----

    public ShopCatalogDto getCatalog(User user) {
        Set<Long> ownedItemIds = userShopItemRepository.findByUser_Id(user.getId()).stream()
                .map(us -> us.getShopItem().getId())
                .collect(Collectors.toSet());

        List<ShopItem> active = shopItemRepository.findByActiveTrue();
        List<ShopItemDto> titles = active.stream()
                .filter(i -> i.getType() == ShopItem.Type.TITLE)
                .map(i -> toDto(i, ownedItemIds.contains(i.getId()), i.getValue().equals(user.getEquippedTitle())))
                .collect(Collectors.toList());
        List<ShopItemDto> colors = active.stream()
                .filter(i -> i.getType() == ShopItem.Type.AVATAR_COLOR)
                .map(i -> toDto(i, ownedItemIds.contains(i.getId()), i.getValue().equals(user.getEquippedAvatarColor())))
                .collect(Collectors.toList());

        return ShopCatalogDto.builder().titles(titles).colors(colors).build();
    }

    @Transactional
    public void purchase(User user, Long itemId) {
        ShopItem item = shopItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        if (!item.isActive()) {
            throw new IllegalArgumentException("판매가 중지된 상품입니다.");
        }
        if (userShopItemRepository.existsByUser_IdAndShopItem_Id(user.getId(), itemId)) {
            throw new IllegalArgumentException("이미 보유한 상품입니다.");
        }

        userPointService.spend(user, item.getPrice(), "상점 구매: " + item.getLabel());

        UserShopItem owned = new UserShopItem();
        owned.setUser(user);
        owned.setShopItem(item);
        userShopItemRepository.save(owned);

        // 구매 즉시 자동 장착 - 바로 효과가 보여야 자연스럽다.
        applyEquip(user, item);
        userRepository.save(user);
    }

    @Transactional
    public void equip(User user, Long itemId) {
        ShopItem item = shopItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        if (!userShopItemRepository.existsByUser_IdAndShopItem_Id(user.getId(), itemId)) {
            throw new IllegalArgumentException("보유하지 않은 상품입니다.");
        }
        applyEquip(user, item);
        userRepository.save(user);
    }

    @Transactional
    public void unequip(User user, ShopItem.Type type) {
        if (type == ShopItem.Type.TITLE) {
            user.setEquippedTitle(null);
        } else {
            user.setEquippedAvatarColor(null);
        }
        userRepository.save(user);
    }

    private void applyEquip(User user, ShopItem item) {
        if (item.getType() == ShopItem.Type.TITLE) {
            user.setEquippedTitle(item.getValue());
        } else {
            user.setEquippedAvatarColor(item.getValue());
        }
    }

    private ShopItemDto toDto(ShopItem item, boolean owned, boolean equipped) {
        return ShopItemDto.builder()
                .id(item.getId())
                .type(item.getType().name())
                .label(item.getLabel())
                .value(item.getValue())
                .price(item.getPrice())
                .active(item.isActive())
                .owned(owned)
                .equipped(equipped)
                .build();
    }
}
