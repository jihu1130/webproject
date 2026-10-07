package com.webschool.webschool.user.account.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그아웃 시 그 계정으로 발급된 JWT를 전부 무효화한다(보안 점검 L2, 2026-10-07). 예전 로그아웃은
// 브라우저 쿠키만 지워서, 공용 PC 등에서 이미 복사된 토큰은 만료(60분)까지 그대로 쓸 수 있었다.
// 토큰을 하나씩 구분하지 않고 계정 단위로 끊기 때문에 **다른 기기의 로그인도 함께 풀린다** -
// 기기별 로그아웃이 필요해지면 토큰에 고유 id(jti)를 넣고 폐기 목록을 따로 둬야 한다.
// 비밀번호 변경/재설정은 이미 User를 들고 있는 서비스가 User.revokeIssuedTokens()를 직접 부른다.
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final UserRepository userRepository;

    @Transactional
    public void revokeAll(String username) {
        userRepository.findByUsername(username).ifPresent(User::revokeIssuedTokens);
    }
}
