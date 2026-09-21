package com.webschool.webschool.user.admin.controller;

import com.webschool.webschool.user.admin.dto.AdminPointLogDto;
import com.webschool.webschool.user.admin.service.AdminPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 포인트 관리(관리자 페이지 재구성, 2026-09-21 추가) - 사이트 전체 포인트 로그 감사. 특정 사용자
// 포인트 지급/차감 액션 자체는 이 화면이 아니라 계정 관리의 사용자 프로필 화면(/admin/users/{id}/profile)에
// 있다 - AdminAccessInterceptor가 "/admin/points"를 canManagePoints로 게이팅한다.
@Controller
@RequestMapping("/admin/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final AdminPointService adminPointService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String keyword, Model model) {
        Page<AdminPointLogDto> logs = adminPointService.getLogs(page, keyword);
        model.addAttribute("logs", logs);
        model.addAttribute("keyword", keyword);
        return "admin/point-log-list";
    }
}
