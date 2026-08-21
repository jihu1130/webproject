package com.webschool.webschool.school.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.school.dto.AdminScheduleCommentSummaryDto;
import com.webschool.webschool.school.service.AdminScheduleCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        boolean deletedView = "deleted".equals(status);
        List<AdminScheduleCommentSummaryDto> filtered = deletedView
                ? adminScheduleCommentService.getDeletedComments(keyword)
                : adminScheduleCommentService.getAllComments(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<AdminScheduleCommentSummaryDto> comments = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("comments", comments);
        model.addAttribute("viewMode", deletedView ? "deleted" : "all");
        model.addAttribute("keyword", keyword);
        return "admin/schedule-comment-list";
    }

    @PostMapping("/{id}/blind")
    public String blind(@PathVariable Long id, @ModelAttribute ListState state) {
        adminScheduleCommentService.setBlind(id, true);
        return state.redirect();
    }

    @PostMapping("/{id}/unblind")
    public String unblind(@PathVariable Long id, @ModelAttribute ListState state) {
        adminScheduleCommentService.setBlind(id, false);
        return state.redirect();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @ModelAttribute ListState state) {
        adminScheduleCommentService.deleteComment(id);
        return state.redirect();
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @ModelAttribute ListState state) {
        adminScheduleCommentService.restoreComment(id);
        return state.redirect();
    }

    @PostMapping("/{id}/clear-report")
    public String clearReport(@PathVariable Long id, @ModelAttribute ListState state) {
        adminScheduleCommentService.clearReport(id);
        return state.redirect();
    }

    @PostMapping("/{id}/unclear-report")
    public String unclearReport(@PathVariable Long id, @ModelAttribute ListState state) {
        adminScheduleCommentService.unclearReport(id);
        return state.redirect();
    }

    // 일괄 처리(체크박스 다중 선택) - AdminPostController.bulkBlind/bulkDelete와 동일 패턴.
    // 신고 목록(/admin/reports)의 한마디 탭에서도 같은 엔드포인트를 재사용한다(admin-bulk.js 참고).
    @PostMapping("/bulk-blind")
    public String bulkBlind(@RequestParam(required = false) List<Long> ids,
                             @RequestParam(required = false) String returnUrl,
                             @ModelAttribute ListState state) {
        if (ids != null) {
            ids.forEach(id -> { try { adminScheduleCommentService.setBlind(id, true); } catch (IllegalArgumentException ignored) { } });
        }
        return resolveReturn(returnUrl, state.redirect());
    }

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(required = false) List<Long> ids,
                              @RequestParam(required = false) String returnUrl,
                              @ModelAttribute ListState state) {
        if (ids != null) {
            ids.forEach(id -> { try { adminScheduleCommentService.deleteComment(id); } catch (IllegalArgumentException ignored) { } });
        }
        return resolveReturn(returnUrl, state.redirect());
    }

    private static String resolveReturn(String returnUrl, String fallback) {
        return (returnUrl != null && returnUrl.startsWith("/admin/")) ? "redirect:" + returnUrl : fallback;
    }

    // 액션(블라인드/삭제/복구 등) 처리 후 원래 보던 검색/필터/페이지 상태로 돌아가기 위한 폼 파라미터
    // 묶음 - 각 액션 폼(admin/schedule-comment-list.html)에 hidden input으로 함께 제출된다.
    public static class ListState {
        private String status;
        private String keyword;
        private int page;
        private Integer size;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public Integer getSize() { return size; }
        public void setSize(Integer size) { this.size = size; }

        String redirect() {
            return PageUtils.buildListRedirect("/admin/schedule-comments",
                    PageUtils.params("status", status, "keyword", keyword), page, PageUtils.normalizeSize(size));
        }
    }
}
