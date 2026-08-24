package com.webschool.webschool.bugreport.controller;

import com.webschool.webschool.bugreport.service.BugReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 버그 리포트 제출 - 비로그인 사용자도 제출 가능(SecurityConfig가 permitAll로 열어둠).
@Controller
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    @GetMapping("/bug-reports/new")
    public String newForm(Authentication authentication, Model model) {
        model.addAttribute("isLoggedIn", isAuthenticated(authentication));
        return "user/bug-report-form";
    }

    @PostMapping("/bug-reports/new")
    public String submit(@RequestParam String title, @RequestParam String content,
                          @RequestParam(required = false) String reporterNickname,
                          @RequestParam(required = false) String contactEmail,
                          @RequestParam(required = false) List<MultipartFile> files,
                          Authentication authentication, Model model) {
        boolean loggedIn = isAuthenticated(authentication);
        try {
            bugReportService.submitReport(loggedIn ? authentication.getName() : null,
                    title, content, reporterNickname, contactEmail, files);
            return "redirect:/bug-reports/new?submitted=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isLoggedIn", loggedIn);
            model.addAttribute("title", title);
            model.addAttribute("content", content);
            model.addAttribute("reporterNickname", reporterNickname);
            model.addAttribute("contactEmail", contactEmail);
            return "user/bug-report-form";
        }
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}
