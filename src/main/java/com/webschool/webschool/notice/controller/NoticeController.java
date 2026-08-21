package com.webschool.webschool.notice.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.notice.dto.NoticeDto;
import com.webschool.webschool.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 사용자용 공지사항 화면. 게시글(/posts)과 완전히 분리된 별도 탭 - 로그인 없이도 조회 가능(SecurityConfig 참고).
@Controller
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size,
                        @RequestParam(required = false) String keyword, Model model) {
        Page<NoticeDto> notices = noticeService.getHistory(page, PageUtils.normalizeSize(size), keyword);
        model.addAttribute("notices", notices);
        model.addAttribute("keyword", keyword);
        return "notice/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("notice", noticeService.getDetail(id));
            return "notice/detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/notices";
        }
    }
}
