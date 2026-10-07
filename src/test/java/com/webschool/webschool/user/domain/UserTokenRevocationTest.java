package com.webschool.webschool.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 보안 점검 L2(2026-10-07) - 로그아웃/비밀번호 변경 시 "그 전에 발급된 토큰은 무효" 판단 회귀 테스트.
class UserTokenRevocationTest {

    @Test
    void accountThatNeverRevoked_acceptsAnyToken() {
        // 이 컬럼이 생기기 전에 만들어진 계정(null) - 배포 직후 기존 로그인이 전부 풀리면 안 된다.
        User user = new User();

        assertFalse(user.isTokenRevoked(Instant.now().minusSeconds(3000)));
    }

    @Test
    void revoke_invalidatesEveryTokenIssuedSoFar_includingRightNow() {
        User user = new User();
        Instant justIssued = Instant.now();

        user.revokeIssuedTokens();

        assertTrue(user.isTokenRevoked(justIssued));
        assertTrue(user.isTokenRevoked(justIssued.minusSeconds(3000)));
    }

    @Test
    void tokenIssuedAtReturnedCutoff_staysValid() {
        // 비밀번호 변경 후 같은 요청에서 다시 내주는 토큰 - 반환된 기준 시각으로 발급하면 유효해야 한다.
        User user = new User();

        Instant cutoff = user.revokeIssuedTokens();

        assertFalse(user.isTokenRevoked(cutoff));
        assertFalse(user.isTokenRevoked(cutoff.plusSeconds(30)));
    }

    // 로그아웃하고 곧바로(같은 초 안에) 다시 로그인해도 새 토큰은 유효해야 한다 - 초 단위로 비교하던
    // 첫 구현에서 실제로 새 로그인이 튕겼던 경우.
    @Test
    void tokenIssuedRightAfterRevoke_staysValid() {
        User user = new User();

        Instant cutoff = user.revokeIssuedTokens();

        assertFalse(user.isTokenRevoked(cutoff.plusMillis(5)));
    }

    @Test
    void tokenWithoutIssuedAt_isRevokedOnceCutoffExists() {
        User user = new User();
        user.revokeIssuedTokens();

        assertTrue(user.isTokenRevoked(null));
    }

    @Test
    void newAccount_rejectsTokensIssuedBeforeItExisted() {
        // 비워진 아이디로 다른 사람이 가입했을 때 옛 주인의 토큰이 통하면 안 된다.
        User user = new User();
        Instant before = Instant.now().minusSeconds(60);

        user.prePersist();

        assertTrue(user.isTokenRevoked(before));
        assertFalse(user.isTokenRevoked(Instant.now()));
    }
}
