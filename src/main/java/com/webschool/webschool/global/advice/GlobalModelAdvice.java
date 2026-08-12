package com.webschool.webschool.global.advice;

import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @ModelAttribute("loginUser")
    public User loginUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    // 네비바 종 아이콘 배지 초기값 - 첫 페이지 로드 시 깜빡임 없이 바로 보이고, 이후엔
    // notification.js가 /notifications/unread-count를 폴링해서 갱신한다.
    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return 0;
        }
        return notificationService.getUnreadCount(authentication.getName());
    }
}
