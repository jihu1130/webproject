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

    // 계정 관리(canManageUsers) 권한이 있다고 해서 다른 사용자의 포인트 잔액을 바꿀 수 있게 하면
    // 안 된다고 판단 - 포인트 지급/차감은 canManagePoints로 별도 게이팅한다(위 promote/demote 등이
    // canManageAdminPermissions로 갈라지는 것과 같은 이유).
    private static final Pattern POINT_ADJUST_ACTION_PATH =
            Pattern.compile("^/admin/users/\\d+/points/adjust$");

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
        if (POINT_ADJUST_ACTION_PATH.matcher(uri).matches()) {
            // 이 액션은 계정 상세(canManageUsers로 게이팅된 /admin/users/{id}/profile) 화면 안의
            // 폼이라 그 접근 권한은 그대로 요구하고, "실제 잔액을 바꾸는" 민감함 때문에 canManagePoints도
            // 추가로 요구한다(둘 다 있어야 통과) - canManagePoints 하나만으로 이 경로에 들어와도
            // 프로필 화면 자체(GET)는 여전히 canManageUsers 없이는 못 보므로 형식적 우회는 안 된다.
            if (!user.isCanManageUsers() || !user.isCanManagePoints()) {
                throw new AccessDeniedException("포인트 지급/차감 권한이 없습니다.");
            }
            return true;
        }
        if (uri.startsWith("/admin/users") && !user.isCanManageUsers()) {
            throw new AccessDeniedException("계정 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/audit-log") && !user.isCanViewAuditLog()) {
            throw new AccessDeniedException("감사 로그 열람 권한이 없습니다.");
        }
        // 보안 로그(로그인 실패/계정 잠금/권한 변경) - 감사 로그와 같은 AdminActionLog 테이블을
        // 걸러 보여주는 화면이라 같은 권한 플래그로 게이팅한다.
        if (uri.startsWith("/admin/security-log") && !user.isCanViewAuditLog()) {
            throw new AccessDeniedException("보안 로그 열람 권한이 없습니다.");
        }
        // 에러 로그 / 문의(버그 리포트) 관리 / 대시보드 / 부하테스트 결과 - 예전엔 총관리자 전용으로
        // 하드코딩돼 있었지만(2026-09-28) 각각 위임 가능한 플래그로 바꿨다. 스택 트레이스·서버 상태처럼
        // 민감한 정보가 있는 화면이라 기본값은 꺼짐이고 총관리자가 명시적으로 켜줘야 한다.
        if (uri.startsWith("/admin/error-log") && !user.isCanViewErrorLog()) {
            throw new AccessDeniedException("에러 로그 열람 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/bug-reports") && !user.isCanManageBugReports()) {
            throw new AccessDeniedException("문의 관리 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/dashboard") && !user.isCanViewDashboard()) {
            throw new AccessDeniedException("대시보드 열람 권한이 없습니다.");
        }
        if (uri.startsWith("/admin/loadtest") && !user.isCanViewLoadTest()) {
            throw new AccessDeniedException("부하테스트 결과 열람 권한이 없습니다.");
        }
        // 게시글/댓글/한마디 관리 화면에서 작성자 이름을 눌러 프로필을 보는 기능(2026-08-10(5차) 추가) -
        // 계정 관리(/admin/users)는 총관리자 전용이지만, 이 조회 전용 화면은 신고/게시글/한마디 관리
        // 권한이 하나라도 있는 부관리자라면 볼 수 있게 한다(그 권한으로 이미 같은 정보(실명 닉네임 등)를
        // 보고 있으므로 새로 노출되는 정보가 없음).
        if (uri.startsWith("/admin/profiles")) {
            // 출석/포인트 관리 화면(2026-09-28 추가)에서도 계정 이름을 눌러 프로필을 열 수 있게
            // 됐으므로, 그 권한을 가진 부관리자도 여기서 막히면 안 된다(이미 같은 정보를 보고
            // 있으므로 새로 노출되는 정보 없음 - 위 신고/게시글/한마디 관리와 같은 논리).
            boolean anyManagePermission = user.isCanManageReports() || user.isCanManagePosts()
                    || user.isCanManageScheduleComments() || user.isCanManageAttendance()
                    || user.isCanManagePoints();
            if (!anyManagePermission) {
                throw new AccessDeniedException("프로필을 조회할 권한이 없습니다.");
            }
            return true;
        }
        if (uri.startsWith("/admin/reports") && !user.isCanManageReports()) {
            throw new AccessDeniedException("신고 관리 권한이 없습니다.");
        }
        // 추천 게시글 관리(/admin/post-recommend, 관리자 페이지 재구성 2026-09-21 추가) - 게시글
        // 관리 화면의 3번째 서브탭이라 별도 권한 플래그 없이 게시글 관리(canManagePosts)와 같은
        // 조건으로 묶는다(댓글 관리가 그런 것과 동일한 판단).
        if ((uri.startsWith("/admin/posts") || uri.startsWith("/admin/comments")
                || uri.startsWith("/admin/post-recommend")) && !user.isCanManagePosts()) {
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
        if (uri.startsWith("/admin/polls") && !user.isCanManagePolls()) {
            throw new AccessDeniedException("설문 관리 권한이 없습니다.");
        }
        // 출석 관리(/admin/attendance, 관리자 페이지 재구성 2026-09-21 추가) - 신규 위임 권한.
        if (uri.startsWith("/admin/attendance") && !user.isCanManageAttendance()) {
            throw new AccessDeniedException("출석 관리 권한이 없습니다.");
        }
        // 포인트 관리(/admin/points, 사이트 전체 포인트 로그 열람) - 신규 위임 권한. 특정 사용자
        // 포인트 지급/차감 액션(/admin/users/{id}/points/adjust)은 위에서 canManageUsers와 함께
        // 별도로 더 엄격하게 게이팅된다.
        if (uri.startsWith("/admin/points") && !user.isCanManagePoints()) {
            throw new AccessDeniedException("포인트 관리 권한이 없습니다.");
        }
        return true;
    }
}
