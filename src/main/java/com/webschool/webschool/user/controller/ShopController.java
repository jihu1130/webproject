package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.domain.ShopItem;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.service.ShopService;
import com.webschool.webschool.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 포인트 소비 상점(todo.md 요구사항) - 칭호/아바타 색상을 구매/장착하는 사용자용 화면.
@Controller
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final UserService userService;

    @GetMapping
    public String shop(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("catalog", shopService.getCatalog(user));
        model.addAttribute("points", user.getPoints());
        return "user/shop";
    }

    @PostMapping("/purchase/{itemId}")
    public String purchase(@PathVariable Long itemId, Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getByUsername(authentication.getName());
            shopService.purchase(user, itemId);
            redirectAttributes.addFlashAttribute("flashSuccess", "구매하고 바로 장착했어요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/shop";
    }

    @PostMapping("/equip/{itemId}")
    public String equip(@PathVariable Long itemId, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getByUsername(authentication.getName());
            shopService.equip(user, itemId);
            redirectAttributes.addFlashAttribute("flashSuccess", "장착했어요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/shop";
    }

    @PostMapping("/unequip/{type}")
    public String unequip(@PathVariable ShopItem.Type type, Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        User user = userService.getByUsername(authentication.getName());
        shopService.unequip(user, type);
        redirectAttributes.addFlashAttribute("flashSuccess", "장착을 해제했어요.");
        return "redirect:/shop";
    }
}
