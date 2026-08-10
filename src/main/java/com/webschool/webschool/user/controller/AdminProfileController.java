package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

// 게시글/댓글/한마디 "관리" 화면에서 작성자 이름을 눌러 프로필을 확인하기 위한 경로.
// 기존 /admin/users/{id}/profile은 "계정 관리"(/admin/users/**) 하위라 AdminAccessInterceptor가
// 총관리자 전용으로 막아둔다 - 그런데 게시글/댓글/한마디 관리는 부관리자도 권한만 있으면 들어오는
// 화면이라, 거기서 작성자 이름을 누르는 것까지 총관리자 전용으로 막으면 정작 그 화면을 보고 있는
// 부관리자가 프로필을 못 보는 문제가 생긴다. 그래서 "/admin/users"와 별개의 경로로 분리하고,
// AdminAccessInterceptor에서 "관리 권한(신고/게시글/한마디 중 하나라도)이 있는 부관리자"까지 허용한다.
// 화면 자체(AdminUserService.getUserProfile())와 템플릿(admin/user-profile.html)은 100% 재사용 -
// 계정 승격/탈퇴 같은 조작 버튼이 애초에 없는 읽기 전용 화면이라 그대로 재사용해도 안전하다.
@Controller
@RequestMapping("/admin/profiles")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminUserService adminUserService;

    @GetMapping("/{id}")
    public String profile(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("profile", adminUserService.getUserProfile(id));
            return "admin/user-profile";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin";
        }
    }
}
