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
import org.springframework.web.bind.annotation.RequestParam;
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

    // from="profile": 마이페이지 "내 프로필 설정"(/mypage/profile)에서도 보유한 치장품을 바로
    // 장착/해제할 수 있게 열어주면서(사용자 요청), 상점 화면에서 누른 것과 같은 폼/엔드포인트를
    // 그대로 재사용하기 위한 최소한의 분기 - 값이 임의 URL이 아니라 고정된 두 목적지 중 하나를
    // 고르는 스위치라 오픈 리다이렉트 위험이 없다.
    @PostMapping("/equip/{itemId}")
    public String equip(@PathVariable Long itemId, @RequestParam(required = false) String from,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getByUsername(authentication.getName());
            shopService.equip(user, itemId);
            redirectAttributes.addFlashAttribute("flashSuccess", "장착했어요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:" + ("profile".equals(from) ? "/mypage/profile" : "/shop");
    }

    @PostMapping("/unequip/{type}")
    public String unequip(@PathVariable ShopItem.Type type, @RequestParam(required = false) String from,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userService.getByUsername(authentication.getName());
        shopService.unequip(user, type);
        redirectAttributes.addFlashAttribute("flashSuccess", "장착을 해제했어요.");
        return "redirect:" + ("profile".equals(from) ? "/mypage/profile" : "/shop");
    }
}
