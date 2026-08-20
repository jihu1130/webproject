package com.webschool.webschool.notice.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.notice.dto.NoticeDto;
import com.webschool.webschool.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// 총관리자 또는 canManageNotices 권한을 받은 부관리자 전용 공지사항 관리 화면.
// AdminAccessInterceptor가 "/admin/notices/**" 접근을 이미 권한으로 막아준다.
@Controller
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size, Model model) {
        Page<NoticeDto> notices = noticeService.getHistory(page, PageUtils.normalizeSize(size));
        model.addAttribute("notices", notices);
        return "admin/notice-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("mode", "create");
        return "admin/notice-form";
    }

    @PostMapping
    public String create(@RequestParam String title, @RequestParam String content,
                          Authentication authentication, Model model) {
        try {
            noticeService.createNotice(authentication.getName(), title, content);
            return "redirect:/admin/notices";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "create");
            model.addAttribute("title", title);
            model.addAttribute("content", content);
            return "admin/notice-form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size, Model model) {
        try {
            model.addAttribute("notice", noticeService.getDetail(id));
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/notices";
        }
        model.addAttribute("listPage", page);
        model.addAttribute("listSize", PageUtils.normalizeSize(size));
        return "admin/notice-detail";
    }

    // 버그수정 프롬포트 요청 - 예전엔 작성 후 수정 자체가 없어서 오타 하나 나도 새 공지를 다시
    // 올려야 했다(그러면 전체 알림이 또 발송됨).
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        try {
            NoticeDto notice = noticeService.getDetail(id);
            model.addAttribute("mode", "edit");
            model.addAttribute("noticeId", id);
            model.addAttribute("title", notice.getTitle());
            model.addAttribute("content", notice.getContent());
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/notices";
        }
        return "admin/notice-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String title, @RequestParam String content,
                          Authentication authentication, Model model) {
        try {
            noticeService.updateNotice(id, authentication.getName(), title, content);
            return "redirect:/admin/notices/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "edit");
            model.addAttribute("noticeId", id);
            model.addAttribute("title", title);
            model.addAttribute("content", content);
            return "admin/notice-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        try {
            noticeService.deleteNotice(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/admin/notices";
    }
}
