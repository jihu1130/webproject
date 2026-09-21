package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserShopItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserShopItemRepository extends JpaRepository<UserShopItem, Long> {
    boolean existsByUser_IdAndShopItem_Id(Long userId, Long shopItemId);
    List<UserShopItem> findByUser_Id(Long userId);

    // 상점 관리 카탈로그 목록의 판매 수량 배지(관리자 페이지 재구성, 2026-09-21 추가).
    long countByShopItem_Id(Long shopItemId);

    // 탈퇴 계정 하드 삭제(AccountHardDeleteService) - 구매/장착 내역은 개인 활동 흔적이라 함께 지운다.
    @Modifying
    @Query("DELETE FROM UserShopItem s WHERE s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
