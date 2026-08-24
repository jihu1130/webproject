package com.webschool.webschool.bugreport.controller;

import com.webschool.webschool.bugreport.dto.BugReportDto;
import com.webschool.webschool.bugreport.service.BugReportService;
import com.webschool.webschool.global.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// 총관리자 전용 버그 리포트 관리 화면. AdminAccessInterceptor가 "/admin/bug-reports"를
// /admin/users, /admin/audit-log와 동일하게 총관리자 전용으로 막아준다(위임 권한 없음).
@Controller
@RequestMapping("/admin/bug-reports")
@RequiredArgsConstructor
public class AdminBugReportController {

    private final BugReportService bugReportService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        Page<BugReportDto> reports = bugReportService.getList(page, PageUtils.normalizeSize(size));
        model.addAttribute("reports", reports);
        return "admin/bug-report-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size, Model model) {
        try {
            model.addAttribute("report", bugReportService.getDetail(id));
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/bug-reports";
        }
        model.addAttribute("listPage", page);
        model.addAttribute("listSize", PageUtils.normalizeSize(size));
        return "admin/bug-report-detail";
    }

    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id, Authentication authentication) {
        try {
            bugReportService.resolve(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/bug-reports";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        try {
            bugReportService.delete(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/bug-reports";
    }
}
