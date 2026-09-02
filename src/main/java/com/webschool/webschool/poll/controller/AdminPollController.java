package com.webschool.webschool.poll.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.poll.dto.PollAdminSummaryDto;
import com.webschool.webschool.poll.service.AdminPollService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 전용 설문 관리 화면(todo.md "설문 후속" 결과 열람 + "고도화 후보" 소프트 삭제 요구사항).
// AdminPostController와 동일한 전체/삭제됨 탭 + 삭제/복구 패턴을 따른다.
@Controller
@RequestMapping("/admin/polls")
@RequiredArgsConstructor
public class AdminPollController {

    private final AdminPollService adminPollService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        boolean deletedView = "deleted".equals(status);
        List<PollAdminSummaryDto> filtered = deletedView ? adminPollService.getDeletedPolls(keyword) : adminPollService.getAllPolls(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<PollAdminSummaryDto> polls = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("polls", polls);
        model.addAttribute("viewMode", deletedView ? "deleted" : "all");
        model.addAttribute("keyword", keyword);
        return "admin/poll-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size,
                          Authentication authentication, Model model) {
        try {
            model.addAttribute("poll", adminPollService.getDetail(id, authentication.getName()));
            model.addAttribute("listStatus", status);
            model.addAttribute("listKeyword", keyword);
            model.addAttribute("listPage", page);
            model.addAttribute("listSize", PageUtils.normalizeSize(size));
            return "admin/poll-detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/polls";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPollService.deletePoll(id);
        return state.redirectToList();
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @ModelAttribute ListState state) {
        adminPollService.restorePoll(id);
        return state.redirectToDetail(id);
    }

    // 액션 처리 후 원래 보던 목록(검색/필터/페이지)으로 정확히 돌아가기 위한 폼 파라미터 묶음
    // (AdminPostController.ListState와 동일 패턴).
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
            return PageUtils.buildListRedirect("/admin/polls",
                    PageUtils.params("status", status, "keyword", keyword), page, PageUtils.normalizeSize(size));
        }

        String redirectToDetail(Long id) {
            return PageUtils.buildListRedirect("/admin/polls/" + id,
                    PageUtils.params("status", status, "keyword", keyword), page, PageUtils.normalizeSize(size));
        }
    }
}
