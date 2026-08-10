package com.webschool.webschool.post.controller;

import com.webschool.webschool.post.dto.AdminPostDetailDto;
import com.webschool.webschool.post.dto.AdminPostSummaryDto;
import com.webschool.webschool.post.service.AdminPostService;
import lombok.RequiredArgsConstructor;
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
                        @RequestParam(required = false) String keyword, Model model) {
        // viewMode: "all"(기본, 전체 게시글) / "deleted"(삭제됨) - 신고 관리는 /admin/reports로 분리됨
        boolean deletedView = "deleted".equals(status);
        List<AdminPostSummaryDto> posts = deletedView ? adminPostService.getDeletedPosts(keyword) : adminPostService.getAllPosts(keyword);
        model.addAttribute("posts", posts);
        model.addAttribute("viewMode", deletedView ? "deleted" : "all");
        model.addAttribute("keyword", keyword);
        return "admin/post-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        try {
            AdminPostDetailDto post = adminPostService.getPostDetail(id);
            model.addAttribute("post", post);
            return "admin/post-detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/posts";
        }
    }

    @PostMapping("/{id}/blind")
    public String blind(@PathVariable Long id) {
        adminPostService.setBlind(id, true);
        return "redirect:/admin/posts/" + id;
    }

    @PostMapping("/{id}/unblind")
    public String unblind(@PathVariable Long id) {
        adminPostService.setBlind(id, false);
        return "redirect:/admin/posts/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        adminPostService.deletePost(id);
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id) {
        adminPostService.restorePost(id);
        return "redirect:/admin/posts/" + id;
    }

    @PostMapping("/{id}/clear-report")
    public String clearReport(@PathVariable Long id) {
        adminPostService.clearReport(id);
        return "redirect:/admin/posts/" + id;
    }

    @PostMapping("/{postId}/comments/{commentId}/clear-report")
    public String clearCommentReport(@PathVariable Long postId, @PathVariable Long commentId) {
        adminPostService.clearCommentReport(commentId);
        return "redirect:/admin/posts/" + postId;
    }
}
