package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.ShopItem;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.ShopItemRepository;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.repository.UserShopItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 상점(ShopService) 구매/장착/해제 회귀 테스트. 지금까지 이 기능은 브라우저로 무료(0P) 상품
// 구매→자동장착→CSS 애니메이션까지 수동 QA만 됐고(todo.md "Phase 0~8 변경사항 QA" 참고)
// 자동 테스트가 없었다 - 특히 "구매 즉시 자동 장착"과 "포인트 부족 시 구매 기록이 남으면
// 안 된다"는 순서 의존적인 규칙, avatar_color 효과가 NONE이면 문자열 "NONE"이 아니라
// null로 저장돼야 한다는 규칙(ShopService.applyEquip())은 코드만 봐서는 지켜지고 있는지
// 확인하기 어려워서 이 파일로 고정해둔다.
@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock private ShopItemRepository shopItemRepository;
    @Mock private UserShopItemRepository userShopItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPointService userPointService;

    @InjectMocks private ShopService shopService;

    private User buyer;
    private ShopItem titleItem;
    private ShopItem colorItemWithEffect;
    private ShopItem colorItemNoEffect;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(1L);
        buyer.setUsername("buyer");
        buyer.setPoints(100);

        titleItem = new ShopItem();
        titleItem.setId(10L);
        titleItem.setType(ShopItem.Type.TITLE);
        titleItem.setLabel("모범생");
        titleItem.setValue("모범생");
        titleItem.setPrice(50);
        titleItem.setActive(true);

        colorItemWithEffect = new ShopItem();
        colorItemWithEffect.setId(20L);
        colorItemWithEffect.setType(ShopItem.Type.AVATAR_COLOR);
        colorItemWithEffect.setLabel("골드");
        colorItemWithEffect.setValue("#d4af37");
        colorItemWithEffect.setPrice(80);
        colorItemWithEffect.setActive(true);
        colorItemWithEffect.setEffect(ShopItem.Effect.SPARKLE);

        colorItemNoEffect = new ShopItem();
        colorItemNoEffect.setId(21L);
        colorItemNoEffect.setType(ShopItem.Type.AVATAR_COLOR);
        colorItemNoEffect.setLabel("네이비");
        colorItemNoEffect.setValue("#1b2a4a");
        colorItemNoEffect.setPrice(0);
        colorItemNoEffect.setActive(true);
        colorItemNoEffect.setEffect(ShopItem.Effect.NONE);
    }

    @Test
    void purchase_throwsWhenItemNotFound() {
        when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> shopService.purchase(buyer, 999L));
    }

    @Test
    void purchase_throwsWhenItemDiscontinued() {
        titleItem.setActive(false);
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));

        assertThrows(IllegalArgumentException.class, () -> shopService.purchase(buyer, 10L));
    }

    @Test
    void purchase_throwsWhenAlreadyOwned() {
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> shopService.purchase(buyer, 10L));
    }

    // 포인트가 부족하면 UserPointService.spend()가 예외를 던지는데(insufficient balance),
    // 이 예외가 그대로 전파돼야 하고 - 더 중요하게는 - 구매 기록(UserShopItem)이 저장되면 안
    // 된다. purchase()에서 spend() 호출이 userShopItemRepository.save()보다 먼저 실행되는
    // 순서에 의존하는 동작이라 회귀에 특히 취약하다.
    @Test
    void purchase_propagatesInsufficientPointsAndSkipsSavingOwnership() {
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).thenReturn(false);
        doThrow(new IllegalArgumentException("포인트가 부족합니다."))
                .when(userPointService).spend(eq(buyer), eq(50), anyString());

        assertThrows(IllegalArgumentException.class, () -> shopService.purchase(buyer, 10L));

        verify(userShopItemRepository, never()).save(any());
        assertNull(buyer.getEquippedTitle());
    }

    @Test
    void purchase_titleItem_spendsPointsAndAutoEquips() {
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).thenReturn(false);

        assertDoesNotThrow(() -> shopService.purchase(buyer, 10L));

        verify(userPointService).spend(eq(buyer), eq(50), anyString());
        verify(userShopItemRepository).save(any());
        verify(userRepository).save(buyer);
        assertEquals("모범생", buyer.getEquippedTitle());
    }

    @Test
    void purchase_avatarColorWithEffect_equipsColorAndCopiesEffectName() {
        when(shopItemRepository.findById(20L)).thenReturn(Optional.of(colorItemWithEffect));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 20L)).thenReturn(false);

        shopService.purchase(buyer, 20L);

        assertEquals("#d4af37", buyer.getEquippedAvatarColor());
        assertEquals("SPARKLE", buyer.getEquippedEffect());
    }

    // ShopService.applyEquip()의 핵심 분기: effect가 NONE이면 문자열 "NONE"이 아니라 null을
    // 저장해야 한다(User.equippedEffect의 "NONE/null이면 효과 없음" 주석 참고) - 실수로
    // effect.name()을 그대로 저장하면 템플릿에서 "NONE" CSS 클래스를 찾다가 조용히 아무
    // 효과도 안 먹거나, 반대로 방어 로직이 없는 곳에서 예상 못한 값으로 새는 회귀가 날 수 있다.
    @Test
    void purchase_avatarColorWithNoEffect_storesNullNotNoneString() {
        when(shopItemRepository.findById(21L)).thenReturn(Optional.of(colorItemNoEffect));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 21L)).thenReturn(false);

        shopService.purchase(buyer, 21L);

        assertEquals("#1b2a4a", buyer.getEquippedAvatarColor());
        assertNull(buyer.getEquippedEffect());
    }

    @Test
    void equip_throwsWhenItemNotFound() {
        when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> shopService.equip(buyer, 999L));
    }

    @Test
    void equip_throwsWhenNotOwned() {
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> shopService.equip(buyer, 10L));
    }

    @Test
    void equip_ownedItem_appliesEquipWithoutSpendingPoints() {
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));
        when(userShopItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).thenReturn(true);

        shopService.equip(buyer, 10L);

        assertEquals("모범생", buyer.getEquippedTitle());
        verify(userPointService, never()).spend(any(), anyInt(), anyString());
    }

    @Test
    void unequip_title_clearsOnlyEquippedTitle() {
        buyer.setEquippedTitle("모범생");
        buyer.setEquippedAvatarColor("#d4af37");
        buyer.setEquippedEffect("SPARKLE");

        shopService.unequip(buyer, ShopItem.Type.TITLE);

        assertNull(buyer.getEquippedTitle());
        assertEquals("#d4af37", buyer.getEquippedAvatarColor());
        assertEquals("SPARKLE", buyer.getEquippedEffect());
    }

    @Test
    void unequip_avatarColor_clearsColorAndEffectTogether() {
        buyer.setEquippedTitle("모범생");
        buyer.setEquippedAvatarColor("#d4af37");
        buyer.setEquippedEffect("SPARKLE");

        shopService.unequip(buyer, ShopItem.Type.AVATAR_COLOR);

        assertEquals("모범생", buyer.getEquippedTitle());
        assertNull(buyer.getEquippedAvatarColor());
        assertNull(buyer.getEquippedEffect());
    }

    @Test
    void createItem_rejectsBlankLabel() {
        assertThrows(IllegalArgumentException.class,
                () -> shopService.createItem(ShopItem.Type.TITLE, "  ", "모범생", 10, null));
    }

    @Test
    void createItem_rejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> shopService.createItem(ShopItem.Type.TITLE, "모범생", "모범생", -1, null));
    }

    // AVATAR_COLOR가 아닌 상품은 관리자가 effect를 실수로 넘겨도 항상 NONE으로 강제된다
    // (ShopService.createItem() - "type == AVATAR_COLOR && effect != null"일 때만 유지).
    // 칭호(TITLE)에 반짝임 효과를 붙이는 건 애초에 의미가 없는 상태라 저장 단계에서 막는다.
    @Test
    void createItem_forcesEffectNoneForNonAvatarColorType() {
        shopService.createItem(ShopItem.Type.TITLE, "모범생", "모범생", 10, ShopItem.Effect.SPARKLE);

        ArgumentCaptor<ShopItem> captor = ArgumentCaptor.forClass(ShopItem.class);
        verify(shopItemRepository).save(captor.capture());
        assertEquals(ShopItem.Effect.NONE, captor.getValue().getEffect());
    }

    @Test
    void createItem_keepsEffectForAvatarColorType() {
        shopService.createItem(ShopItem.Type.AVATAR_COLOR, "골드", "#d4af37", 80, ShopItem.Effect.SPARKLE);

        ArgumentCaptor<ShopItem> captor = ArgumentCaptor.forClass(ShopItem.class);
        verify(shopItemRepository).save(captor.capture());
        assertEquals(ShopItem.Effect.SPARKLE, captor.getValue().getEffect());
    }

    @Test
    void updateItem_throwsWhenItemNotFound() {
        when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> shopService.updateItem(999L, "모범생", "모범생", 10, null));
    }

    @Test
    void setActive_throwsWhenItemNotFound() {
        when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> shopService.setActive(999L, false));
    }

    // 하드 삭제 대신 판매중지만 지원한다(CLAUDE.md "알려진 함정" - 이미 구매한 사용자의
    // UserShopItem FK가 깨지는 걸 막기 위함). 판매중지돼도 기존 보유/장착 상태에는
    // 영향이 없어야 한다는 걸 함께 확인한다.
    @Test
    void setActive_discontinuesItemWithoutTouchingExistingOwnership() {
        when(shopItemRepository.findById(10L)).thenReturn(Optional.of(titleItem));

        shopService.setActive(10L, false);

        assertFalse(titleItem.isActive());
        verify(userShopItemRepository, never()).delete(any());
    }
}
