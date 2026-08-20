package com.webschool.webschool.post.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.post.dto.AdminCommentReportSummaryDto;
import com.webschool.webschool.post.service.AdminPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자(ROLE_ADMIN) 전용 "댓글 관리" 화면 - AdminPostController/AdminScheduleCommentController와
// 동일 패턴(전체/삭제됨 탭 + 검색 + 인라인 액션). 예전엔 신고된 댓글만 /admin/reports?type=comment에서
// 볼 수 있었고, 평범한 댓글은 게시글 상세를 하나씩 열어야만 확인 가능했다(사용자 지적) - 이 화면은
// 게시글과 무관하게 댓글만 전체 훑어보고 그 자리에서 블라인드/삭제/복구/문제없음 처리까지 할 수 있게
// 한다. 권한은 게시글 관리(canManagePosts)와 같이 묶는다(AdminAccessInterceptor 참고) - 댓글은
// 게시글에 종속된 하위 리소스라 별도 권한 플래그를 새로 만들지 않기로 판단함.
@Controller
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminPostService adminPostService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        boolean deletedView = "deleted".equals(status);
        List<AdminCommentReportSummaryDto> filtered = deletedView
                ? adminPostService.getDeletedComments(keyword)
                : adminPostService.getAllComments(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<AdminCommentReportSummaryDto> comments = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("comments", comments);
        model.addAttribute("viewMode", deletedView ? "deleted" : "all");
        model.addAttribute("keyword", keyword);
        return "admin/comment-list";
    }

    @PostMapping("/{id}/blind")
    public String blind(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.setCommentBlind(id, true);
        return state.redirect();
    }

    @PostMapping("/{id}/unblind")
    public String unblind(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.setCommentBlind(id, false);
        return state.redirect();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.deleteComment(id);
        return state.redirect();
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.restoreComment(id);
        return state.redirect();
    }

    @PostMapping("/{id}/clear-report")
    public String clearReport(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.clearCommentReport(id);
        return state.redirect();
    }

    @PostMapping("/{id}/unclear-report")
    public String unclearReport(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.unclearCommentReport(id);
        return state.redirect();
    }

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
            return PageUtils.buildListRedirect("/admin/comments",
                    PageUtils.params("status", status, "keyword", keyword), page, PageUtils.normalizeSize(size));
        }
    }
}
