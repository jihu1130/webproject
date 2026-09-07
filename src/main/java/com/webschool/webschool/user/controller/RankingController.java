package com.webschool.webschool.user.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 포인트/티어 랭킹 페이지(todo.md 요구사항) - 공개 프로필(/users/{uuid})이 이미 포인트·티어를
// permitAll로 보여주고 있어 같은 공개 수준으로 로그인 없이도 열람 가능하게 둔다(SecurityConfig 참고).
@Controller
@RequiredArgsConstructor
public class RankingController {

    private final UserPointService userPointService;

    @GetMapping("/ranking")
    public String ranking(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(required = false) Integer size,
                           Model model) {
        model.addAttribute("ranking", userPointService.getRanking(Math.max(page, 0), PageUtils.normalizeSize(size)));
        return "user/ranking";
    }
}
