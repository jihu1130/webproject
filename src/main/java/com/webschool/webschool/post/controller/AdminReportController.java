package com.webschool.webschool.post.controller;

import com.webschool.webschool.post.service.AdminPostService;
import com.webschool.webschool.school.service.AdminScheduleCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 관리자 1단계 "신고 관리" - 게시글/댓글/오늘의 한마디 신고 현황을 한 곳에서 모아본다.
// 실제 조치(블라인드/문제없음 처리/삭제 등)는 AdminPostController(게시글 상세 화면) /
// AdminScheduleCommentController(한마디 관리 목록)의 기존 액션을 그대로 사용한다.
@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminPostService adminPostService;
    private final AdminScheduleCommentService adminScheduleCommentService;

    @GetMapping
    public String list(@RequestParam(required = false) String type, Model model) {
        boolean commentView = "comment".equals(type);
        boolean scheduleView = "schedule".equals(type);
        if (scheduleView) {
            model.addAttribute("scheduleComments", adminScheduleCommentService.getReportedComments(null));
        } else if (commentView) {
            model.addAttribute("comments", adminPostService.getReportedComments(null));
        } else {
            model.addAttribute("posts", adminPostService.getReportedPosts(null));
        }
        model.addAttribute("commentView", commentView);
        model.addAttribute("scheduleView", scheduleView);
        return "admin/report-list";
    }
}
