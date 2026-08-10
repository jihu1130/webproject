package com.webschool.webschool.school.controller;

import com.webschool.webschool.school.dto.AdminScheduleCommentSummaryDto;
import com.webschool.webschool.school.service.AdminScheduleCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자(ROLE_ADMIN) 전용 "오늘의 한마디" 관리 화면. SecurityConfig에서 /admin/**은 ROLE_ADMIN만 접근 가능.
// AdminPostController와 동일한 패턴(전체/삭제됨 탭 + 블라인드/문제없음/강제삭제/복구).
@Controller
@RequestMapping("/admin/schedule-comments")
@RequiredArgsConstructor
public class AdminScheduleCommentController {

    private final AdminScheduleCommentService adminScheduleCommentService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                        @RequestParam(required = false) String keyword, Model model) {
        boolean deletedView = "deleted".equals(status);
        List<AdminScheduleCommentSummaryDto> comments = deletedView
                ? adminScheduleCommentService.getDeletedComments(keyword)
                : adminScheduleCommentService.getAllComments(keyword);
        model.addAttribute("comments", comments);
        model.addAttribute("viewMode", deletedView ? "deleted" : "all");
        model.addAttribute("keyword", keyword);
        return "admin/schedule-comment-list";
    }

    @PostMapping("/{id}/blind")
    public String blind(@PathVariable Long id) {
        adminScheduleCommentService.setBlind(id, true);
        return "redirect:/admin/schedule-comments";
    }

    @PostMapping("/{id}/unblind")
    public String unblind(@PathVariable Long id) {
        adminScheduleCommentService.setBlind(id, false);
        return "redirect:/admin/schedule-comments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        adminScheduleCommentService.deleteComment(id);
        return "redirect:/admin/schedule-comments";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id) {
        adminScheduleCommentService.restoreComment(id);
        return "redirect:/admin/schedule-comments";
    }

    @PostMapping("/{id}/clear-report")
    public String clearReport(@PathVariable Long id) {
        adminScheduleCommentService.clearReport(id);
        return "redirect:/admin/schedule-comments";
    }
}
