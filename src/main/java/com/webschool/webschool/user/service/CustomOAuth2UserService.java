package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

// 구글 로그인 - 로컬 계정(아이디/비번 직접 가입)과 완전히 별개로 취급한다(2026-08-13 사용자 확정
// 정책 - 이메일이 같아도 자동 연동하지 않음). 처음 로그인하는 구글 계정이면 이 자리에서 바로 User를
// 만든다(별도 회원가입 폼 없음 - 학교/학년/반 정보는 나중에 마이페이지에서 채우면 됨, User 엔티티의
// 해당 필드들이 전부 nullable이라 가능).
//
// 핵심 트릭: DefaultOAuth2User를 만들 때 nameAttributeKey를 구글의 "sub"가 아니라 우리가 직접 넣은
// "username" 속성으로 지정한다 - 이렇게 하면 Authentication.getName()이 우리 내부 username을
// 반환하게 되어서, 이 앱 전체에 이미 퍼져있는 `userRepository.findByUsername(authentication.getName())`
// 패턴이 폼 로그인/구글 로그인 양쪽에서 전혀 코드 변경 없이 그대로 동작한다.
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Pattern USERNAME_SANITIZE = Pattern.compile("[^A-Za-z0-9]");
    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPenaltyService userPenaltyService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String providerId = oAuth2User.getName(); // 구글의 "sub" 클레임(고유 식별자)
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        User user = userRepository.findByProviderAndProviderId(User.Provider.GOOGLE, providerId)
                .orElseGet(() -> createGoogleUser(providerId, email, name));

        // 이메일 필드가 생기기 전에 가입한 기존 구글 계정 백필 - 구글이 이미 이 이메일의 소유권을
        // 검증했으므로 별도 인증 메일 없이 바로 인증됨으로 채운다. 다른 계정이 그 사이 같은 이메일을
        // 선점했으면(극히 드문 경우) 조용히 건너뛰고, EmailSetupInterceptor가 다음 요청에서 안내한다.
        if (user.needsEmailSetup() && email != null && !email.isBlank()
                && !userRepository.existsByEmail(email)) {
            user.setEmail(email);
            user.setEmailVerified(true);
        }

        // 폼 로그인(CustomUserDetailsService)과 동일한 로그인 차단 조건 - 여기서 빠뜨리면 총관리자가
        // 계정을 정지/탈퇴 처리해도 구글 로그인으로는 그대로 들어와지는 구멍이 생긴다.
        if (user.isDeleted() || !user.isActive() || userPenaltyService.isDeactivated(user.getId())) {
            throw new OAuth2AuthenticationException("이 계정은 로그인할 수 없습니다.");
        }

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("username", user.getUsername());

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority(user.getRole().name())),
                attributes,
                "username"
        );
    }

    private User createGoogleUser(String providerId, String email, String name) {
        String base = (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "user";
        String username = generateUsername(base);

        User user = new User();
        user.setUsername(username);
        // 본인도 모르는 임의 비밀번호 - 구글 계정은 폼 로그인을 쓰지 않는다(User.password 필드 주석 참고)
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(name != null && !name.isBlank() ? truncate(name, MAX_NICKNAME_LENGTH) : username);
        user.setProvider(User.Provider.GOOGLE);
        user.setProviderId(providerId);
        user.setRole(User.Role.ROLE_USER);
        // 구글이 이미 이 이메일의 소유권을 검증했으므로 별도 인증 메일 없이 바로 인증됨 처리
        if (email != null && !email.isBlank() && !userRepository.existsByEmail(email)) {
            user.setEmail(email);
            user.setEmailVerified(true);
        }
        return userRepository.save(user);
    }

    // 아이디는 영문+숫자만 허용(UserService.USERNAME_PATTERN과 동일한 규칙)이라 이메일의 점/특수문자를
    // 전부 제거하고, 중복이면 숫자를 붙여가며 유일해질 때까지 시도한다.
    private String generateUsername(String base) {
        String cleaned = USERNAME_SANITIZE.matcher(base).replaceAll("").toLowerCase();
        if (cleaned.isBlank()) {
            cleaned = "user";
        }
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40);
        }
        String candidate = cleaned;
        int suffix = 0;
        while (userRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = cleaned + suffix;
        }
        return candidate;
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
