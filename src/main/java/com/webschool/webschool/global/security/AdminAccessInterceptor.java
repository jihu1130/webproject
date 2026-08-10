package com.webschool.webschool.global.security;

import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// SecurityConfig가 "/admin/**"은 ROLE_ADMIN/ROLE_SUPER_ADMIN까지만 통과시키는데, 그 안에서도
// 부관리자(ROLE_ADMIN)는 총관리자가 개별로 켜준 권한(신고/게시글/한마디 관리)만 접근할 수 있고
// 계정 관리(/admin/users)는 총관리자(ROLE_SUPER_ADMIN) 전용이다. ROLE_SUPER_ADMIN은 항상 전체 허용.
@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true; // SecurityConfig가 이미 인증을 요구하므로 여기 도달하면 정상적으로는 없는 경우
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || user.isSuperAdmin()) {
            return true;
        }

        if (uri.startsWith("/admin/users")) {
            throw new AccessDeniedException("계정 관리는 총관리자만 접근할 수 있습니다.");
        }
        if (uri.startsWith("/admin/reports") && !user.isCanManageReports()) {
            throw new AccessDeniedException("신고 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/posts") && !user.isCanManagePosts()) {
            throw new AccessDeniedException("게시글 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/schedule-comments") && !user.isCanManageScheduleComments()) {
            throw new AccessDeniedException("한마디 관리 권한이 없습니다.");
        }
        return true;
    }
}
