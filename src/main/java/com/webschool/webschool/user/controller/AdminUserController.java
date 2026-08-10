package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// 총관리자(ROLE_SUPER_ADMIN) 전용 계정 관리 화면. AdminAccessInterceptor가 "/admin/users/**" 전체를
// 총관리자만 접근 가능하도록 막아준다(SecurityConfig의 "/admin/**"은 부관리자까지 통과시키는 것과 다름).
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

    // "1개의 관리자 대시보드 페이지" - 총관리자가 부관리자별로 신고/게시글/한마디 관리 권한을 켜고 끄는 화면
    @GetMapping("/admins")
    public String admins(Model model) {
        model.addAttribute("admins", adminUserService.getAllAdmins());
        return "admin/admin-permissions";
    }

    @GetMapping("/{id}/profile")
    public String profile(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("profile", adminUserService.getUserProfile(id));
            return "admin/user-profile";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/users";
        }
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

    @PostMapping("/{id}/permissions")
    public String permissions(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean canManageReports,
                               @RequestParam(defaultValue = "false") boolean canManagePosts,
                               @RequestParam(defaultValue = "false") boolean canManageScheduleComments) {
        try {
            adminUserService.updatePermissions(id, canManageReports, canManagePosts, canManageScheduleComments);
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users/admins";
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

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, Authentication authentication) {
        try {
            adminUserService.deactivateUser(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id) {
        adminUserService.activateUser(id);
        return "redirect:/admin/users";
    }
}
