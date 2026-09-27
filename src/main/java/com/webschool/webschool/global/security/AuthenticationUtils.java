package com.webschool.webschool.global.security;

import org.springframework.security.core.Authentication;

// "실제로 로그인한 사용자인가" 판단 - permitAll 경로에서는 Authentication이 null이거나
// 익명 토큰(principal = "anonymousUser")으로 들어오므로 둘 다 걸러야 한다. 컨트롤러마다
// 같은 조건식을 복사해 쓰던 것을 모은 공용 헬퍼(2026-09-28 파일 정리).
public final class AuthenticationUtils {

    private AuthenticationUtils() {
    }

    public static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    // 로그인했으면 아이디, 아니면 null - 서비스 계층이 null을 "비로그인"으로 처리하는 곳에 그대로 넘긴다.
    public static String usernameOrNull(Authentication authentication) {
        return isLoggedIn(authentication) ? authentication.getName() : null;
    }
}
