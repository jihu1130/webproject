package com.webschool.webschool.admin.controller;

import com.webschool.webschool.admin.dto.AdminActionLogDto;
import com.webschool.webschool.admin.service.AdminActionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// 관리자 액션 감사 로그 목록 - 총관리자 전용(AdminAccessInterceptor가 "/admin/audit-log"를 이미
// 총관리자 전용으로 막는다).
@Controller
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
public class AdminActionLogController {

    private final AdminActionLogService adminActionLogService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String adminUsername,
                        @RequestParam(required = false) String targetType,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        Page<AdminActionLogDto> logs = adminActionLogService.getLogs(page, adminUsername, targetType, from, to);
        model.addAttribute("logs", logs);
        model.addAttribute("adminUsername", adminUsername);
        model.addAttribute("targetType", targetType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/audit-log";
    }
}
