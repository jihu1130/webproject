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

import java.util.regex.Pattern;

// SecurityConfig가 "/admin/**"은 ROLE_ADMIN/ROLE_SUPER_ADMIN까지만 통과시키는데, 그 안에서도
// 부관리자(ROLE_ADMIN)는 총관리자가 개별로 켜준 권한만 접근할 수 있다. ROLE_SUPER_ADMIN은 항상 전체 허용.
// "/admin/comments"(댓글 관리, 2026-08-10(4차) 추가)는 게시글에 종속된 하위 리소스라 별도 권한
// 플래그 없이 게시글 관리(canManagePosts)와 같은 권한으로 묶는다.
//
// 수정사항.md #12 지적으로 계정 관리(canManageUsers)/관리자 권한 부여(canManageAdminPermissions)/
// 감사 로그(canViewAuditLog) 3개도 다른 4개와 동일하게 위임 가능한 플래그로 바뀌었다(예전엔 이
// 셋만 하드코딩으로 총관리자 전용이었음). "/admin/users/**" 하위에서도 역할/권한 자체를 바꾸는
// promote·demote·permissions·admins(권한 부여 화면)는 canManageAdminPermissions로 더 세밀하게
// 나누고, 계정 목록/프로필/정지/탈퇴 같은 나머지는 canManageUsers로 가른다 - 두 권한을 분리한
// 이유는 "계정을 정지시킬 수 있는 것"과 "다른 계정에게 관리자 권한을 몰아줄 수 있는 것"은 위험도가
// 다른 별개의 권한이기 때문(문서의 권한 상승 우려를 최소화하는 방향).
@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

    // /admin/users/admins(권한 부여 화면) 또는 /admin/users/{id}/promote|demote|permissions|promote-super
    // (역할/권한 변경 액션) - 나머지 /admin/users/** 는 전부 일반 계정 관리(canManageUsers)로 취급한다.
    // promote-super는 이 게이트를 통과해도 AdminUserService.promoteToSuperAdmin()이 별도로
    // isSuperAdmin()을 재확인하므로 이중으로 막힌다(위 클래스 주석 참고).
    private static final Pattern ADMIN_PERMISSION_ACTION_PATH =
            Pattern.compile("^/admin/users/\\d+/(promote|demote|permissions|promote-super)$");

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

        if (uri.equals("/admin/users/admins") || ADMIN_PERMISSION_ACTION_PATH.matcher(uri).matches()) {
            if (!user.isCanManageAdminPermissions()) {
                throw new AccessDeniedException("관리자 권한 부여 권한이 없습니다.");
            }
            return true;
        }
        if (uri.startsWith("/admin/users") && !user.isCanManageUsers()) {
            throw new AccessDeniedException("계정 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/audit-log") && !user.isCanViewAuditLog()) {
            throw new AccessDeniedException("감사 로그 열람 권한이 없습니다.");
        }
        // 버그 리포트 관리 - 위임 권한 플래그 없이 총관리자 전용으로 고정(사용자 확정, 이번 라운드 범위 밖).
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
        if (uri.startsWith("/admin/shop-items") && !user.isCanManageShop()) {
            throw new AccessDeniedException("상점 관리 권한이 없습니다.");
        }
        return true;
    }
}
