package com.webschool.webschool.global.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 세션 인증 → JWT 전체 교체(2026-09-14)의 발급/검증 로직 회귀 테스트. jwt.secret이 없을 때도
// 앱이 죽지 않고 임의 키로 기동한다는 것까지 포함해서 확인한다(CLAUDE.md "알려진 함정" 패턴).
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";

    @Test
    void validToken_isAccepted() {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("test1");

        assertEquals("test1", jwtService.validateAndGetUsername(token).orElseThrow());
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("test1");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertTrue(jwtService.validateAndGetUsername(tampered).isEmpty());
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        JwtService issuer = new JwtService(SECRET);
        JwtService verifier = new JwtService("a-completely-different-secret-key-32bytes");
        String token = issuer.generateToken("test1");

        assertTrue(verifier.validateAndGetUsername(token).isEmpty());
    }

    @Test
    void garbageInput_isRejectedWithoutThrowing() {
        JwtService jwtService = new JwtService(SECRET);

        assertTrue(jwtService.validateAndGetUsername("not-a-jwt-at-all").isEmpty());
    }

    @Test
    void blankSecret_stillBootsAndIssuesUsableTokens() {
        // jwt.secret이 비어있는 환경(로컬 최초 세팅 등)에서도 기동이 죽지 않고, 그 프로세스
        // 안에서는 발급/검증이 자체적으로 일관되게 동작해야 한다(JwtService.init() 참고).
        JwtService jwtService = new JwtService("");
        String token = jwtService.generateToken("test1");

        assertEquals("test1", jwtService.validateAndGetUsername(token).orElseThrow());
    }
}
