package com.webschool.webschool.global.security.jwt;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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

    // "아는 기기" 토큰은 로그인 토큰과 같은 키로 서명하지만 로그인에는 쓸 수 없어야 한다 - 180일짜리라
    // jwt 쿠키 자리에 넣어 통하면 60분 만료가 무의미해진다.
    @Test
    void knownDeviceToken_cannotBeUsedAsLoginToken_andViceVersa() {
        JwtService jwtService = new JwtService(SECRET);
        String deviceToken = jwtService.generateKnownDeviceToken("test1");
        String loginToken = jwtService.generateToken("test1");

        assertTrue(jwtService.parse(deviceToken).isEmpty());
        assertTrue(jwtService.isKnownDeviceTokenFor(deviceToken, "TEST1"));
        assertTrue(!jwtService.isKnownDeviceTokenFor(deviceToken, "test2"));
        assertTrue(!jwtService.isKnownDeviceTokenFor(loginToken, "test1"));
        assertTrue(!jwtService.isKnownDeviceTokenFor("garbage", "test1"));
    }

    // 보안 점검 L2 - 로그아웃/비밀번호 변경 이전 토큰을 가려내려면 발급 시각이 밀리초까지 그대로 읽혀야 한다
    // (초 단위로 잘리면 로그아웃 직후 같은 초에 다시 로그인한 토큰이 무효가 된다).
    @Test
    void parse_returnsIssuedAtWithMillis() {
        JwtService jwtService = new JwtService(SECRET);
        Instant issuedAt = Instant.ofEpochMilli(System.currentTimeMillis() - 120_123);
        String token = jwtService.generateToken("test1", issuedAt);

        JwtService.TokenClaims claims = jwtService.parse(token).orElseThrow();
        assertEquals("test1", claims.username());
        assertEquals(issuedAt, claims.issuedAt());
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("test1");
        // 서명의 "마지막" 글자를 바꾸면 안 된다 - HS256 서명(32바이트)의 base64url 마지막 글자는 하위 2비트가
        // 쓰이지 않아서, 끝 글자가 Y/Z/b일 때 a로 바꾸면 디코딩 결과가 같아 가끔(약 5%) 변조가 안 된 토큰이 된다
        // (2026-09-30 전체 테스트 중 간헐 실패로 발견). 모든 비트가 쓰이는 서명 첫 글자를 바꾼다.
        int sigStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart) + (first == 'A' ? 'B' : 'A') + token.substring(sigStart + 1);

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
