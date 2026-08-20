package com.webschool.webschool.post.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.post.dto.AdminCommentReportSummaryDto;
import com.webschool.webschool.post.dto.AdminPostSummaryDto;
import com.webschool.webschool.post.service.AdminPostService;
import com.webschool.webschool.school.dto.AdminScheduleCommentSummaryDto;
import com.webschool.webschool.school.service.AdminScheduleCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// 관리자 1단계 "신고 관리" - 게시글/댓글/오늘의 한마디 신고 현황을 한 곳에서 모아본다.
// 실제 조치(블라인드/문제없음 처리/삭제 등)는 AdminPostController(게시글 상세 화면) /
// AdminScheduleCommentController(한마디 관리 목록)의 기존 액션을 그대로 사용한다.
// **버그 수정**: 서비스 레이어(getReportedPosts/getReportedComments)는 이미 keyword 필터링을
// 지원하는데 이 컨트롤러가 항상 null을 넘겨서 검색창 자체가 없었다 - keyword/page/size 파라미터를
// 추가하고 다른 관리자 목록 화면과 동일한 패턴(admin-search-form + post-pagination)으로 맞춘다.
@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminPostService adminPostService;
    private final AdminScheduleCommentService adminScheduleCommentService;

    @GetMapping
    public String list(@RequestParam(required = false) String type,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        boolean commentView = "comment".equals(type);
        boolean scheduleView = "schedule".equals(type);
        int pageSize = PageUtils.normalizeSize(size);

        if (scheduleView) {
            List<AdminScheduleCommentSummaryDto> filtered = adminScheduleCommentService.getReportedComments(keyword);
            model.addAttribute("scheduleComments", PageUtils.paginate(filtered, page, pageSize));
        } else if (commentView) {
            List<AdminCommentReportSummaryDto> filtered = adminPostService.getReportedComments(keyword);
            model.addAttribute("comments", PageUtils.paginate(filtered, page, pageSize));
        } else {
            List<AdminPostSummaryDto> filtered = adminPostService.getReportedPosts(keyword);
            model.addAttribute("posts", PageUtils.paginate(filtered, page, pageSize));
        }
        model.addAttribute("commentView", commentView);
        model.addAttribute("scheduleView", scheduleView);
        model.addAttribute("keyword", keyword);
        return "admin/report-list";
    }
}
