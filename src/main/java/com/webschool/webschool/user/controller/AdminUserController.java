package com.webschool.webschool.user.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.dto.AdminUserSummaryDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserPenalty;
import com.webschool.webschool.user.service.AdminUserService;
import com.webschool.webschool.user.service.UserPenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

// 총관리자(ROLE_SUPER_ADMIN) 전용 계정 관리 화면. AdminAccessInterceptor가 "/admin/users/**" 전체를
// 총관리자만 접근 가능하도록 막아준다(SecurityConfig의 "/admin/**"은 부관리자까지 통과시키는 것과 다름).
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserPenaltyService userPenaltyService;

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

    // 버그 수정: 예전엔 여기 8개 액션 전부 성공/실패 여부가 화면에 전혀 안 보이고 그냥 새로고침만
    // 됐다(체크박스 하나 잘못 눌러도 아무 표시 없이 반영됨). flash 메시지로 결과를 알려준다.
    @PostMapping("/{id}/promote")
    public String promote(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state,
                           RedirectAttributes redirectAttributes) {
        try {
            adminUserService.setRole(id, User.Role.ROLE_ADMIN, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", "부관리자로 승격했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return state.redirect();
    }

    @PostMapping("/{id}/demote")
    public String demote(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state,
                          RedirectAttributes redirectAttributes) {
        try {
            adminUserService.setRole(id, User.Role.ROLE_USER, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", "일반 회원으로 강등했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return state.redirect();
    }

    @PostMapping("/{id}/permissions")
    public String permissions(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean canManageReports,
                               @RequestParam(defaultValue = "false") boolean canManagePosts,
                               @RequestParam(defaultValue = "false") boolean canManageScheduleComments,
                               @RequestParam(defaultValue = "false") boolean canManageNotices,
                               RedirectAttributes redirectAttributes) {
        try {
            adminUserService.updatePermissions(id, canManageReports, canManagePosts, canManageScheduleComments, canManageNotices);
            redirectAttributes.addFlashAttribute("flashSuccess", "권한이 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/users/admins";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state,
                          RedirectAttributes redirectAttributes) {
        try {
            adminUserService.deleteUser(id, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", "계정을 탈퇴 처리했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return state.redirect();
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @ModelAttribute ListState state, RedirectAttributes redirectAttributes) {
        adminUserService.restoreUser(id);
        redirectAttributes.addFlashAttribute("flashSuccess", "계정을 복구했습니다.");
        return state.redirect();
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, Authentication authentication, @ModelAttribute ListState state,
                              RedirectAttributes redirectAttributes) {
        try {
            adminUserService.deactivateUser(id, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", "계정을 비활성화(정지)했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return state.redirect();
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, @ModelAttribute ListState state, RedirectAttributes redirectAttributes) {
        adminUserService.activateUser(id);
        redirectAttributes.addFlashAttribute("flashSuccess", "계정을 다시 활성화했습니다.");
        return state.redirect();
    }

    // 수정사항.md 지적(#7·#9) - 게시글/댓글/한마디 관리엔 이미 있는 일괄 처리가 계정 관리에만
    // 빠져있었다. AdminPostController.bulkBlind()/bulkDelete()와 완전히 동일한 모양 - id별로
    // 기존 단건 메서드를 그대로 호출하고(본인/총관리자 보호는 그 메서드들이 이미 함), 개별
    // IllegalArgumentException은 한 건이 실패해도 나머지를 계속 처리하도록 무시한다.
    @PostMapping("/bulk-deactivate")
    public String bulkDeactivate(@RequestParam(required = false) List<Long> ids,
                                  @RequestParam(required = false) String returnUrl,
                                  Authentication authentication) {
        if (ids != null) {
            ids.forEach(id -> {
                try {
                    adminUserService.deactivateUser(id, authentication.getName());
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
        return resolveReturn(returnUrl);
    }

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(required = false) List<Long> ids,
                              @RequestParam(required = false) String returnUrl,
                              Authentication authentication) {
        if (ids != null) {
            ids.forEach(id -> {
                try {
                    adminUserService.deleteUser(id, authentication.getName());
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
        return resolveReturn(returnUrl);
    }

    private static String resolveReturn(String returnUrl) {
        return (returnUrl != null && returnUrl.startsWith("/admin/")) ? "redirect:" + returnUrl : "redirect:/admin/users";
    }

    @PostMapping("/{id}/penalties")
    public String issuePenalty(@PathVariable Long id,
                                @RequestParam UserPenalty.Type type,
                                @RequestParam String reason,
                                @RequestParam(required = false) Integer durationDays,
                                Authentication authentication) {
        try {
            userPenaltyService.issue(id, type, reason, durationDays, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users/" + id + "/profile";
    }

    @PostMapping("/{id}/penalties/{penaltyId}/revoke")
    public String revokePenalty(@PathVariable Long id, @PathVariable Long penaltyId) {
        try {
            userPenaltyService.revoke(penaltyId);
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/users/" + id + "/profile";
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
