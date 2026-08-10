package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// 관리자(ROLE_ADMIN) 전용 계정 관리 화면. SecurityConfig에서 /admin/**은 ROLE_ADMIN만 접근 가능.
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", adminUserService.getAllUsers());
        return "admin/user-list";
    }

    @PostMapping("/{id}/promote")
    public String promote(@PathVariable Long id, Authentication authentication) {
        try {
            adminUserService.setRole(id, User.Role.ROLE_ADMIN, authentication.getName());
        } catch (IllegalArgumentException ignored) {
            // 목록으로 돌아가서 상태 그대로 보여주면 충분 (자기 자신 변경 시도 등)
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/demote")
    public String demote(@PathVariable Long id, Authentication authentication) {
        try {
            adminUserService.setRole(id, User.Role.ROLE_USER, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        try {
            adminUserService.deleteUser(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id) {
        adminUserService.restoreUser(id);
        return "redirect:/admin/users";
    }
}
