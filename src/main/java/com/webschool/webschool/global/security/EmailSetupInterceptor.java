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

    // /school-setup, /password-setup: 다른 온보딩 게이트의 화면도 함께 허용 - SchoolSetupInterceptor의
    // 동일 주석 참고(무한 리다이렉트 방지, 온보딩 화면 3개는 서로를 막지 않음).
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/email-setup", "/school-setup", "/password-setup",
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
        if (user == null || !user.needsEmailSetup()) {
            return true;
        }

        // 구글 계정은 이 게이트를 적용하지 않는다 - 이메일이 비어있는 이유가 대부분 구글 이메일이
        // 이미 다른 계정(본인의 로컬 계정 등)에 등록돼 있어 CustomOAuth2UserService가 백필을
        // 건너뛴 경우인데, 그렇다고 여기서 "다른" 이메일을 새로 등록시키는 건 사용자에게 혼란만
        // 준다(실사용자 신고 - "구글 로그인했는데 이메일을 하나 더 등록하라고 한다"). 게이트의
        // 원래 목적인 "비밀번호 찾기 가능하게" 자체도 구글 계정에는 적용되지 않는다 -
        // UserService.requestPasswordReset()이 구글 계정이면 토큰 발급 없이 안내 메일만 보내고
        // 끝나므로, 이 계정의 email 필드가 비어있어도 잃는 기능이 없다.
        if (user.getProvider() == User.Provider.GOOGLE) {
            return true;
        }

        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        response.sendRedirect("/email-setup");
        return false;
    }
}
