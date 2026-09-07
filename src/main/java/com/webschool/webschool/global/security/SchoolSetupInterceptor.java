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
    // /email-setup, /password-setup: 다른 온보딩 게이트의 화면도 함께 허용해야 한다 - 신규 구글
    // 가입은 학교 설정과 비밀번호 설정이 동시에 필요한데(EmailSetupInterceptor 대상인 "이메일 필드가
    // 생기기 전의 기존 계정"과 달리, 이 둘은 겹치는 대상), 여기 없으면 /school-setup에 막 진입한
    // 사용자를 PasswordSetupInterceptor가 다시 /password-setup으로 튕기고 거기서 이 인터셉터가 또
    // /school-setup으로 튕기는 무한 리다이렉트가 발생한다(실사용자 신고로 발견,
    // PasswordSetupInterceptor 도입 시점). 온보딩 화면 3개는 서로를 막지 않고, 각자 자기 요건이
    // 남아있는 동안만 "그 외" 화면 접근을 막는 방식으로 나란히 동작한다.
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/school-setup", "/email-setup", "/password-setup",
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
