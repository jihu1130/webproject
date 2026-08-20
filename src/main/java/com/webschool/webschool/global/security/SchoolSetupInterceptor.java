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

// 구글 소셜 로그인으로 처음 가입하면 학교/학년/반이 빈 채로 계정이 만들어진다(CustomOAuth2UserService
// 참고, 로컬 회원가입은 이 정보가 항상 필수라 이 상태가 나오지 않는다). 이 상태로 커뮤니티/캘린더 등
// "우리 학교" 기준 기능을 쓰면 의미가 없어지므로, 학교 설정을 마치기 전까지는 /school-setup 화면
// 외에는 아무 곳도 접근하지 못하게 막는다(로그아웃은 예외 - 막힌 상태에서 빠져나갈 수 있어야 함).
@Component
@RequiredArgsConstructor
public class SchoolSetupInterceptor implements HandlerInterceptor {

    // /notifications/unread-count: 네비바가 로그인 상태면 항상 폴링하는 배지 API라 막으면 콘솔에
    // 계속 오류만 남는다 - 화면(/notifications) 자체는 막힌 상태 그대로 유지된다.
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/school-setup", "/logout", "/notifications/unread-count"
    );

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !user.needsSchoolSetup()) {
            return true;
        }

        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        response.sendRedirect("/school-setup");
        return false;
    }
}
