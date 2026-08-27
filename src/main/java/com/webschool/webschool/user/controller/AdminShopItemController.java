package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.domain.ShopItem;
import com.webschool.webschool.user.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// 총관리자 또는 canManageShop 권한을 받은 부관리자 전용 상점 카탈로그 관리 화면.
// AdminAccessInterceptor가 "/admin/shop-items/**" 접근을 이미 권한으로 막아준다.
// AdminNoticeController와 동일한 목록+폼 구조를 그대로 따른다.
@Controller
@RequestMapping("/admin/shop-items")
@RequiredArgsConstructor
public class AdminShopItemController {

    private final ShopService shopService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", shopService.getAllItems());
        return "admin/shop-item-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("mode", "create");
        return "admin/shop-item-form";
    }

    @PostMapping
    public String create(@RequestParam ShopItem.Type type, @RequestParam String label,
                          @RequestParam String value, @RequestParam int price, Model model) {
        try {
            shopService.createItem(type, label, value, price);
            return "redirect:/admin/shop-items";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "create");
            model.addAttribute("type", type.name());
            model.addAttribute("label", label);
            model.addAttribute("value", value);
            model.addAttribute("price", price);
            return "admin/shop-item-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var item = shopService.getAllItems().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (item == null) {
            return "redirect:/admin/shop-items";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("itemId", id);
        model.addAttribute("type", item.getType());
        model.addAttribute("label", item.getLabel());
        model.addAttribute("value", item.getValue());
        model.addAttribute("price", item.getPrice());
        return "admin/shop-item-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String label,
                          @RequestParam String value, @RequestParam int price, Model model) {
        try {
            shopService.updateItem(id, label, value, price);
            return "redirect:/admin/shop-items";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "edit");
            model.addAttribute("itemId", id);
            model.addAttribute("label", label);
            model.addAttribute("value", value);
            model.addAttribute("price", price);
            return "admin/shop-item-form";
        }
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable Long id, @RequestParam boolean active) {
        try {
            shopService.setActive(id, active);
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/shop-items";
    }
}
