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

// 관리자 전용 설문 결과 열람 화면(todo.md "설문 후속" 요구사항). Poll에는 삭제/블라인드 액션이
// 없어(poll 패키지에 소프트 딜리트 미적용) 조회 전용 - AdminProfileController와 동일한 성격.
@Controller
@RequestMapping("/admin/polls")
@RequiredArgsConstructor
public class AdminPollController {

    private final AdminPollService adminPollService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        List<PollAdminSummaryDto> filtered = adminPollService.getAllPolls(keyword);
        int pageSize = PageUtils.normalizeSize(size);
        Page<PollAdminSummaryDto> polls = PageUtils.paginate(filtered, page, pageSize);
        model.addAttribute("polls", polls);
        model.addAttribute("keyword", keyword);
        return "admin/poll-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size,
                          Authentication authentication, Model model) {
        try {
            model.addAttribute("poll", adminPollService.getDetail(id, authentication.getName()));
            model.addAttribute("listKeyword", keyword);
            model.addAttribute("listPage", page);
            model.addAttribute("listSize", PageUtils.normalizeSize(size));
            return "admin/poll-detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/polls";
        }
    }
}
