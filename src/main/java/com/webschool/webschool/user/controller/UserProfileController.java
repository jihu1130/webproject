package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 커뮤니티(일반 사용자) 공개 프로필 - SecurityConfig에서 GET /users/*는 permitAll(게시글 목록/상세와
// 동일하게 로그인 없이도 열람 가능). 게시글 목록/상세/댓글, "오늘의 한마디"에서 작성자 이름을 누르면
// 여기로 온다(authorLinkable이 true인 경우만 - 익명 게시물/탈퇴 계정은 링크 자체가 안 걸림).
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{uuid}")
    public String profile(@PathVariable String uuid, @RequestParam(defaultValue = "0") int page, Model model) {
        try {
            Long id = userProfileService.resolveIdByUuid(uuid);
            model.addAttribute("profile", userProfileService.getProfile(id, page));
            return "user/profile";
        } catch (IllegalArgumentException e) {
            return "redirect:/posts";
        }
    }
}
