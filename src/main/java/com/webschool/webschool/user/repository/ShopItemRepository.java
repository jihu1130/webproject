package com.webschool.webschool.user.repository;

import com.webschool.webschool.user.domain.ShopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {
    List<ShopItem> findByActiveTrue();
}
