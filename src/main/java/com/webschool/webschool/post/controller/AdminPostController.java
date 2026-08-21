package com.webschool.webschool.post.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.post.dto.AdminPostDetailDto;
import com.webschool.webschool.post.dto.AdminPostSummaryDto;
import com.webschool.webschool.post.service.AdminPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자(ROLE_ADMIN) 전용 게시물 관리 화면. SecurityConfig에서 /admin/**은 ROLE_ADMIN만 접근 가능.
@Controller
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        // viewMode: "all"(기본, 전체 게시글) / "deleted"(삭제됨) - 신고 관리는 /admin/reports로 분리됨
        boolean deletedView = "deleted".equals(status);
        List<AdminPostSummaryDto> filtered = deletedView ? adminPostService.getDeletedPosts(keyword) : adminPostService.getAllPosts(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<AdminPostSummaryDto> posts = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("posts", posts);
        model.addAttribute("viewMode", deletedView ? "deleted" : "all");
        model.addAttribute("keyword", keyword);
        return "admin/post-list";
    }

    // status/keyword/page/size: 게시글 목록에서 이 상세로 넘어올 때 들고 온 검색/필터/페이지 상태.
    // "목록으로" 링크와 이 화면의 액션 폼(blind/unblind/restore/clear-report)들이 그대로 다시 들고
    // 다녀서, 검색해서 찾은 글을 조치해도 목록으로 돌아갔을 때 검색 조건이 유지되게 한다.
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size, Model model) {
        try {
            AdminPostDetailDto post = adminPostService.getPostDetail(id);
            model.addAttribute("post", post);
            model.addAttribute("listStatus", status);
            model.addAttribute("listKeyword", keyword);
            model.addAttribute("listPage", page);
            model.addAttribute("listSize", PageUtils.normalizeSize(size));
            return "admin/post-detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/posts";
        }
    }

    @PostMapping("/{id}/blind")
    public String blind(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.setBlind(id, true);
        return state.redirectToDetail(id);
    }

    @PostMapping("/{id}/unblind")
    public String unblind(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.setBlind(id, false);
        return state.redirectToDetail(id);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.deletePost(id);
        return state.redirectToList();
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.restorePost(id);
        return state.redirectToDetail(id);
    }

    @PostMapping("/{id}/clear-report")
    public String clearReport(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.clearReport(id);
        return state.redirectToDetail(id);
    }

    @PostMapping("/{id}/unclear-report")
    public String unclearReport(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPostService.unclearReport(id);
        return state.redirectToDetail(id);
    }

    // 일괄 처리(체크박스 다중 선택) - 이 목록 화면(/admin/posts)뿐 아니라 신고 목록(/admin/reports)의
    // 게시물 탭에서도 같은 엔드포인트를 재사용한다(admin-bulk.js 참고). returnUrl로 호출한 화면의
    // 현재 검색/필터/페이지 URL을 그대로 넘겨받아 처리 후 그 자리로 돌아간다 - 관리자 영역
    // 밖으로는 리다이렉트하지 않도록 접두사를 검증한다(오픈 리다이렉트 방지).
    @PostMapping("/bulk-blind")
    public String bulkBlind(@RequestParam(required = false) List<Long> ids,
                             @RequestParam(required = false) String returnUrl,
                             @ModelAttribute ListState state) {
        if (ids != null) {
            ids.forEach(id -> { try { adminPostService.setBlind(id, true); } catch (IllegalArgumentException ignored) { } });
        }
        return resolveReturn(returnUrl, state.redirectToList());
    }

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(required = false) List<Long> ids,
                              @RequestParam(required = false) String returnUrl,
                              @ModelAttribute ListState state) {
        if (ids != null) {
            ids.forEach(id -> { try { adminPostService.deletePost(id); } catch (IllegalArgumentException ignored) { } });
        }
        return resolveReturn(returnUrl, state.redirectToList());
    }

    private static String resolveReturn(String returnUrl, String fallback) {
        return (returnUrl != null && returnUrl.startsWith("/admin/")) ? "redirect:" + returnUrl : fallback;
    }

    @PostMapping("/{postId}/comments/{commentId}/clear-report")
    public String clearCommentReport(@PathVariable Long postId, @PathVariable Long commentId, @ModelAttribute ListState state) {
        adminPostService.clearCommentReport(commentId);
        return state.redirectToDetail(postId);
    }

    @PostMapping("/{postId}/comments/{commentId}/unclear-report")
    public String unclearCommentReport(@PathVariable Long postId, @PathVariable Long commentId, @ModelAttribute ListState state) {
        adminPostService.unclearCommentReport(commentId);
        return state.redirectToDetail(postId);
    }

    // 액션 처리 후 원래 보던 목록(검색/필터/페이지)으로 정확히 돌아가기 위한 폼 파라미터 묶음.
    // 상세 화면(detail())이 model에 심어둔 listStatus/listKeyword/listPage/listSize를 각 액션
    // 폼의 hidden input으로 그대로 되돌려 받는다.
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

        String redirectToList() {
            return PageUtils.buildListRedirect("/admin/posts",
                    PageUtils.params("status", status, "keyword", keyword), page, PageUtils.normalizeSize(size));
        }

        String redirectToDetail(Long id) {
            return PageUtils.buildListRedirect("/admin/posts/" + id,
                    PageUtils.params("status", status, "keyword", keyword), page, PageUtils.normalizeSize(size));
        }
    }
}
