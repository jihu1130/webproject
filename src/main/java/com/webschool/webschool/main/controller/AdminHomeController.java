package com.webschool.webschool.main.controller;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
        // 버그 수정 - 공지사항 작성 권한만 켜진 부관리자는 위 세 분기에 안 걸려서 접근거부로
        // 튕겨나갔다(/admin/notices 자체는 문제없이 들어갈 수 있는데 진입 라우팅만 빠졌던 케이스).
        if (user.isCanManageNotices()) {
            return "redirect:/admin/notices";
        }
        return "redirect:/admin/access-denied";
    }

    @GetMapping("/admin/access-denied")
    public String accessDenied(HttpSession session, Model model) {
        // SecurityConfig의 accessDeniedHandler가 세션에 심어둔 구체적인 사유 - 한 번 보여주면
        // 바로 지운다(진짜 flash와 동일하게 새로고침하면 다시 안 보여야 함).
        Object reason = session.getAttribute("flashError");
        if (reason != null) {
            model.addAttribute("flashError", reason);
            session.removeAttribute("flashError");
        }
        return "admin/access-denied";
    }
}
