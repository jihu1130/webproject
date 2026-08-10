package com.webschool.webschool.user.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.dto.AdminUserSummaryDto;
import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 총관리자(ROLE_SUPER_ADMIN) 전용 계정 관리 화면. AdminAccessInterceptor가 "/admin/users/**" 전체를
// 총관리자만 접근 가능하도록 막아준다(SecurityConfig의 "/admin/**"은 부관리자까지 통과시키는 것과 다름).
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        List<AdminUserSummaryDto> filtered = adminUserService.getAllUsers(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<AdminUserSummaryDto> users = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
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
    public String promote(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state) {
        try {
            adminUserService.setRole(id, User.Role.ROLE_ADMIN, authentication.getName());
        } catch (IllegalArgumentException ignored) {
            // 목록으로 돌아가서 상태 그대로 보여주면 충분 (자기 자신 변경 시도 등)
        }
        return state.redirect();
    }

    @PostMapping("/{id}/demote")
    public String demote(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state) {
        try {
            adminUserService.setRole(id, User.Role.ROLE_USER, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return state.redirect();
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
    public String delete(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state) {
        try {
            adminUserService.deleteUser(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return state.redirect();
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @ModelAttribute ListState state) {
        adminUserService.restoreUser(id);
        return state.redirect();
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state) {
        try {
            adminUserService.deactivateUser(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return state.redirect();
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, @ModelAttribute ListState state) {
        adminUserService.activateUser(id);
        return state.redirect();
    }

    // 액션(승격/해제/탈퇴/복구/비활성화/활성화) 처리 후 검색어/페이지를 유지한 채 목록으로 돌아가기
    // 위한 폼 파라미터 묶음 - status는 없음(계정 관리엔 상태 탭이 없어서).
    public static class ListState {
        private String keyword;
        private int page;
        private Integer size;

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public Integer getSize() { return size; }
        public void setSize(Integer size) { this.size = size; }

        String redirect() {
            return PageUtils.buildListRedirect("/admin/users",
                    PageUtils.params("keyword", keyword), page, PageUtils.normalizeSize(size));
        }
    }
}
