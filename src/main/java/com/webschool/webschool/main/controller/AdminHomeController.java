package com.webschool.webschool.main.controller;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// "/admin" 진입점 - 계정이 접근 가능한 첫 번째 메뉴로 자동 이동시킨다(총관리자는 항상 신고 관리부터).
// 접근 가능한 메뉴가 하나도 없는 부관리자(총관리자가 아직 아무 권한도 안 켜준 상태)는 안내 화면으로 보낸다.
@Controller
@RequiredArgsConstructor
public class AdminHomeController {

    private final UserRepository userRepository;

    @GetMapping("/admin")
    public String index(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return "redirect:/admin/access-denied";
        }
        if (user.isSuperAdmin() || user.isCanManageReports()) {
            return "redirect:/admin/reports";
        }
        if (user.isCanManagePosts()) {
            return "redirect:/admin/posts";
        }
        if (user.isCanManageScheduleComments()) {
            return "redirect:/admin/schedule-comments";
        }
        return "redirect:/admin/access-denied";
    }

    @GetMapping("/admin/access-denied")
    public String accessDenied() {
        return "admin/access-denied";
    }
}
