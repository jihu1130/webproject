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

// 구글 소셜 로그인으로 처음 가입하면 본인도 모르는 임의 비밀번호로 계정이 만들어진다
// (CustomOAuth2UserService.createGoogleUser() 참고, 로컬 회원가입은 가입 시 항상 실제 비밀번호를
// 받으므로 이 상태가 나오지 않는다). todo.md #19 - 가입 시점에 실제 비밀번호를 설정하도록 강제하기로
// 확정해서, 이 설정을 마치기 전까지는 /password-setup 화면 외에는 접근하지 못하게 막는다
// (SchoolSetupInterceptor/EmailSetupInterceptor와 동일한 패턴, 로그아웃은 예외).
@Component
@RequiredArgsConstructor
public class PasswordSetupInterceptor implements HandlerInterceptor {

    // /school-setup, /email-setup: 다른 온보딩 게이트의 화면도 함께 허용 - SchoolSetupInterceptor의
    // 동일 주석 참고(무한 리다이렉트 방지, 온보딩 화면 3개는 서로를 막지 않음). 이게 없으면 학교
    // 설정도 같이 필요한 신규 구글 가입자가 /school-setup ↔ /password-setup 사이를 무한 리다이렉트한다.
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/password-setup", "/school-setup", "/email-setup",
            "/logout", "/notifications/unread-count"
    );

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !user.needsPasswordSetup()) {
            return true;
        }

        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        response.sendRedirect("/password-setup");
        return false;
    }
}
