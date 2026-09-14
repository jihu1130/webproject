package com.webschool.webschool.global.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

// 세션 인증 → JWT 전체 교체(사용자 확정, 2026-09-14)의 발급/검증 담당. subject는 항상 내부
// username(CustomOAuth2UserService가 구글 로그인에도 동일하게 맞춰둔 값)이라, 이 토큰만
// 검증하면 로그인 방식과 무관하게 "누구인지"를 알 수 있다 - JwtAuthenticationFilter 참고.
@Slf4j
@Service
public class JwtService {

    public static final String COOKIE_NAME = "jwt";
    private static final Duration EXPIRATION = Duration.ofMinutes(60);

    // "알려진 함정" 패턴 준수 - jwt.secret이 아직 application.yml에 없는 환경(로컬/운영
    // 최초 배포 등)에서도 앱이 기동은 되게 기본값(빈 문자열)을 준다. 빈 값이면 매 기동마다
    // 새로 생성한 임의 키를 그 프로세스 동안만 쓴다(재시작하면 기존 토큰은 전부 무효화되고
    // 재로그인이 필요해짐) - "기능이 죽지 않고 조용히 약화"되는 다른 선택 키들과 같은 원칙.
    // 운영 배포 전에는 반드시 강한 랜덤 값을 채워서 재시작해도 토큰이 유지되게 할 것.
    @Value("${jwt.secret:}")
    private String configuredSecret;

    private SecretKey key;

    public JwtService() {
    }

    // 테스트 전용 - @Value 주입 없이 바로 키를 세팅한다(JwtServiceTest/JwtAuthenticationFilterTest).
    JwtService(String secretForTesting) {
        this.configuredSecret = secretForTesting;
        init();
    }

    @PostConstruct
    void init() {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("jwt.secret이 설정되지 않아 임의 키로 기동합니다 - 재시작하면 기존 로그인 토큰이 전부 무효화됩니다. "
                    + "운영 배포 전 application.yml에 강한 랜덤 값을 반드시 채워 넣을 것.");
            key = Jwts.SIG.HS256.key().build();
        } else {
            key = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(EXPIRATION)))
                .signWith(key)
                .compact();
    }

    public long expirationSeconds() {
        return EXPIRATION.toSeconds();
    }

    // secure 플래그를 요청 시점의 실제 프로토콜(request.isSecure())로 판단한다 - 무조건
    // true로 고정하면 로컬 개발(http://localhost:8888, CLAUDE.md 기준)에서 브라우저가 쿠키
    // 자체를 저장하지 않아 로그인이 조용히 안 되는 함정이 생긴다. 운영은 nginx가
    // forward-headers-strategy: framework로 X-Forwarded-Proto를 넘겨주므로 https로 정확히 판단됨.
    public ResponseCookie buildCookie(String token, boolean secureRequest) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureRequest)
                .sameSite("Lax")
                .path("/")
                .maxAge(EXPIRATION)
                .build();
    }

    public ResponseCookie buildExpiredCookie(boolean secureRequest) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureRequest)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    // 서명/만료 검증 실패 시 예외를 던지지 않고 empty를 반환한다 - 호출부(JwtAuthenticationFilter)가
    // 조용히 익명 처리로 넘어가게 하기 위함(만료/변조된 쿠키 하나 때문에 500이 나면 안 됨).
    public Optional<String> validateAndGetUsername(String token) {
        try {
            String username = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(username);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
