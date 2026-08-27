package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.UserShopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserShopItemRepository extends JpaRepository<UserShopItem, Long> {
    boolean existsByUser_IdAndShopItem_Id(Long userId, Long shopItemId);
    List<UserShopItem> findByUser_Id(Long userId);
}
