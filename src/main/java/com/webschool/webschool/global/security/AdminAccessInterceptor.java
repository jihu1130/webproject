package com.webschool.webschool.global.security;

import com.webschool.webschool.user.domain.User;
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
// 부관리자(ROLE_ADMIN)는 총관리자가 개별로 켜준 권한(신고/게시글/한마디/공지사항 관리)만 접근할 수
// 있고 계정 관리(/admin/users)는 총관리자(ROLE_SUPER_ADMIN) 전용이다. ROLE_SUPER_ADMIN은 항상 전체 허용.
// "/admin/comments"(댓글 관리, 2026-08-10(4차) 추가)는 게시글에 종속된 하위 리소스라 별도 권한
// 플래그 없이 게시글 관리(canManagePosts)와 같은 권한으로 묶는다.
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
        // 감사 로그(어떤 관리자가 언제 어떤 조치를 했는지)는 관리자 활동 전체를 들여다보는 화면이라
        // 계정 관리와 동일하게 총관리자 전용으로 막는다.
        if (uri.startsWith("/admin/audit-log")) {
            throw new AccessDeniedException("감사 로그는 총관리자만 접근할 수 있습니다.");
        }
        // 버그 리포트 관리 - 위임 권한 플래그 없이 계정 관리/감사 로그와 동일하게 총관리자 전용으로 고정.
        if (uri.startsWith("/admin/bug-reports")) {
            throw new AccessDeniedException("버그 리포트 관리는 총관리자만 접근할 수 있습니다.");
        }
        // 게시글/댓글/한마디 관리 화면에서 작성자 이름을 눌러 프로필을 보는 기능(2026-08-10(5차) 추가) -
        // 계정 관리(/admin/users)는 총관리자 전용이지만, 이 조회 전용 화면은 신고/게시글/한마디 관리
        // 권한이 하나라도 있는 부관리자라면 볼 수 있게 한다(그 권한으로 이미 같은 정보(실명 닉네임 등)를
        // 보고 있으므로 새로 노출되는 정보가 없음).
        if (uri.startsWith("/admin/profiles")) {
            boolean anyManagePermission = user.isCanManageReports() || user.isCanManagePosts()
                    || user.isCanManageScheduleComments();
            if (!anyManagePermission) {
                throw new AccessDeniedException("프로필을 조회할 권한이 없습니다.");
            }
            return true;
        }
        if (uri.startsWith("/admin/reports") && !user.isCanManageReports()) {
            throw new AccessDeniedException("신고 관리 권한이 없습니다.");
        }
        if ((uri.startsWith("/admin/posts") || uri.startsWith("/admin/comments")) && !user.isCanManagePosts()) {
            throw new AccessDeniedException("게시글 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/schedule-comments") && !user.isCanManageScheduleComments()) {
            throw new AccessDeniedException("한마디 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/notices") && !user.isCanManageNotices()) {
            throw new AccessDeniedException("공지사항 작성 권한이 없습니다.");
        }
        return true;
    }
}
