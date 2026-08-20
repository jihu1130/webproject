package com.webschool.webschool.notification.controller;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.notification.dto.NotificationDto;
import com.webschool.webschool.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Integer size,
                        Authentication authentication, Model model) {
        Page<NotificationDto> notifications = notificationService.getPage(
                authentication.getName(), page, PageUtils.normalizeSize(size));
        model.addAttribute("notifications", notifications);
        return "notification/list";
    }

    // 알림 행을 클릭(폼 제출)하면 읽음 처리 후 원래 이동해야 할 링크로 리다이렉트한다.
    // 링크가 없는 알림(계정 정지 등)은 알림 목록에 그대로 머무른다.
    @PostMapping("/{id}/read")
    public String read(@PathVariable Long id, Authentication authentication) {
        try {
            String link = notificationService.markRead(id, authentication.getName());
            return "redirect:" + (link != null && !link.isBlank() ? link : "/notifications");
        } catch (IllegalArgumentException e) {
            return "redirect:/notifications";
        }
    }

    @PostMapping("/read-all")
    public String readAll(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
        return "redirect:/notifications";
    }

    // 버그수정 프롬포트 요청 - 개별 알림 삭제(그동안 "모두 읽음 처리"만 있었음)
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        try {
            notificationService.delete(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/notifications";
    }

    // 네비바 종 아이콘 배지 폴링용 (비로그인 상태면 0)
    @GetMapping("/unread-count")
    @ResponseBody
    public Map<String, Long> unreadCount(Authentication authentication) {
        String username = (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) ? authentication.getName() : null;
        return Map.of("count", notificationService.getUnreadCount(username));
    }
}
