package com.webschool.webschool.bugreport.controller;

import com.webschool.webschool.bugreport.service.BugReportService;
import com.webschool.webschool.global.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String submit(@RequestParam(required = false) String category,
                          @RequestParam String title, @RequestParam String content,
                          @RequestParam(required = false) String reporterNickname,
                          @RequestParam(required = false) String contactEmail,
                          @RequestParam(required = false) List<MultipartFile> files,
                          Authentication authentication, Model model) {
        boolean loggedIn = isAuthenticated(authentication);
        try {
            bugReportService.submitReport(loggedIn ? authentication.getName() : null,
                    category, title, content, reporterNickname, contactEmail, files);
            return "redirect:/bug-reports/new?submitted=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isLoggedIn", loggedIn);
            model.addAttribute("category", category);
            model.addAttribute("title", title);
            model.addAttribute("content", content);
            model.addAttribute("reporterNickname", reporterNickname);
            model.addAttribute("contactEmail", contactEmail);
            return "user/bug-report-form";
        }
    }

    // "내 문의" 화면(todo.md 항목 - 문의 답변 스레드가 생겼는데 정작 로그인 사용자가 자기 문의에
    // 달린 관리자 답변을 앱 안에서 볼 방법이 없던 걸 뒤늦게 발견해서 추가함) - BugReportService.
    // getMyInquiries()는 이미 있었지만 이걸 호출하는 컨트롤러가 없었다.
    @GetMapping("/mypage/inquiries")
    public String myInquiries(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(required = false) Integer size,
                               Authentication authentication, Model model) {
        model.addAttribute("inquiries",
                bugReportService.getMyInquiries(authentication.getName(), page, PageUtils.normalizeSize(size)));
        return "user/my-inquiries";
    }

    // 로그인한 제출자가 본인 문의에 재답장 - 답변이 와도 다시 답장할 방법이 없던 문제(사용자 지적)로
    // 추가. 비로그인 익명 제출은 애초에 이 화면(/mypage/inquiries)에 들어올 계정이 없어 해당 없음.
    @PostMapping("/mypage/inquiries/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam String content,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) Integer size,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            bugReportService.addUserReply(id, authentication.getName(), content);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/mypage/inquiries?page=" + page + "&size=" + PageUtils.normalizeSize(size);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}
