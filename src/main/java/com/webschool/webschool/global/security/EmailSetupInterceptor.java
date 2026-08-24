package com.webschool.webschool.global.security;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

// 이메일 필드가 생기기 전에 만들어진 기존 계정(admin, user1~5 등)은 email이 비어있다 - 다음 로그인
// 시 이메일 입력을 강제한다(사용자 확정 정책, SchoolSetupInterceptor와 동일한 패턴). 이메일 인증
// 자체는 강제하지 않지만(마이페이지 배지로만 안내), "이메일이 아예 등록조차 안 된" 상태는 비밀번호
// 찾기가 원천적으로 불가능해지므로 이것만은 게이트로 막는다. 신규 가입/구글 로그인은 가입 시점에
// 이메일이 항상 채워지므로(UserService.register(), CustomOAuth2UserService) 이 게이트에 걸리지 않는다.
@Component
@RequiredArgsConstructor
public class EmailSetupInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/email-setup", "/logout", "/notifications/unread-count"
    );

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !user.needsEmailSetup()) {
            return true;
        }

        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        response.sendRedirect("/email-setup");
        return false;
    }
}
