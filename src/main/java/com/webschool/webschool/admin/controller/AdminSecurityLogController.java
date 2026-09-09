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

// 보안 로그 목록 - 감사 로그(AdminActionLogController)와 같은 AdminActionLog 테이블을 쓰지만,
// 로그인 실패/계정 잠금/권한·역할 변경처럼 "보안 관점에서 봐야 하는" action만 걸러서 보여준다.
// 감사 로그와 같은 canViewAuditLog 권한으로 게이팅한다(AdminAccessInterceptor 참고) - 이미 그
// 권한이 있는 부관리자는 PROMOTE/DEMOTE/PERMISSIONS 같은 계정 변경 조치를 감사 로그에서도 볼 수
// 있으므로 새로 노출되는 정보가 없다.
@Controller
@RequestMapping("/admin/security-log")
@RequiredArgsConstructor
public class AdminSecurityLogController {

    private final AdminActionLogService adminActionLogService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String action,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        Page<AdminActionLogDto> logs = adminActionLogService.getSecurityLogs(page, action, from, to);
        model.addAttribute("logs", logs);
        model.addAttribute("action", action);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/security-log";
    }
}
