package com.webschool.webschool.user.account.service;

import com.webschool.webschool.user.domain.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;

// Spring Security의 기본 UserDetails에 "이 시각 이전에 발급된 JWT는 무효"(User.tokensInvalidBefore)만
// 얹은 것(보안 점검 L2). JwtAuthenticationFilter가 매 요청 CustomUserDetailsService로 사용자를 다시
// 읽으므로, 여기에 값을 실어 보내면 토큰 무효화 확인 때문에 DB를 한 번 더 조회하지 않아도 된다.
public class AccountUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long tokensInvalidBefore;

    public AccountUserDetails(UserDetails base, Long tokensInvalidBefore) {
        super(base.getUsername(), base.getPassword(), base.isEnabled(), base.isAccountNonExpired(),
                base.isCredentialsNonExpired(), base.isAccountNonLocked(), base.getAuthorities());
        this.tokensInvalidBefore = tokensInvalidBefore;
    }

    public boolean isTokenRevoked(Instant issuedAt) {
        return User.isTokenRevoked(tokensInvalidBefore, issuedAt);
    }
}
