package com.webschool.webschool.global.security.captcha;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// 캡차(Cloudflare Turnstile, 2026-10-12). 쓰는 곳 두 군데:
// - 회원가입: 항상 확인(AuthController) - 계정을 자동으로 찍어내는 것을 막는다.
// - 로그인: 평소엔 안 보이고, 연속 실패로 대기가 걸렸을 때만 보인다. 통과하면 기다리지 않고 바로 다시
//   시도할 수 있다(LoginThrottleFilter) - 남이 내 아이디로 틀려서 걸린 대기를 본인이 풀 수 있게.
//
// 키(captcha.turnstile.site-key/secret-key)가 비어 있으면 캡차 전체가 꺼진다: 화면에 위젯이 안 나오고,
// 가입은 확인 없이 통과, 로그인 대기는 예전처럼 기다려야 한다. 로컬 개발은 키 없이 그대로 쓰면 된다.
// 화면에서 Cloudflare 스크립트/iframe을 불러오므로 CspHeaderWriter의 허용 출처와 짝이다.
@Service
public class CaptchaService {

    // 위젯이 폼 안에 자동으로 넣는 hidden input 이름(Turnstile 고정값).
    public static final String TOKEN_PARAMETER = "cf-turnstile-response";

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI VERIFY_URI = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String siteKey;
    private final String secretKey;

    public CaptchaService(@Value("${captcha.turnstile.site-key:}") String siteKey,
                          @Value("${captcha.turnstile.secret-key:}") String secretKey) {
        this.siteKey = siteKey == null ? "" : siteKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    // 둘 중 하나만 채워져 있으면 꺼진 것으로 본다 - 위젯만 뜨고 검증을 못 하면 아무도 가입할 수 없다.
    public boolean isEnabled() {
        return !siteKey.isEmpty() && !secretKey.isEmpty();
    }

    // 화면에 넣을 사이트 키(공개 값). 꺼져 있으면 null - 템플릿은 th:if로 위젯을 통째로 뺀다.
    public String siteKeyOrNull() {
        return isEnabled() ? siteKey : null;
    }

    public boolean verify(HttpServletRequest request, String clientIp) {
        return verify(request.getParameter(TOKEN_PARAMETER), clientIp);
    }

    // 사용자가 캡차를 통과했는지 Cloudflare에 확인한다. 토큰은 한 번만 쓸 수 있고 5분 뒤 만료된다.
    // Cloudflare에 연결하지 못하면 통과시키지 않는다(false) - 통과시키면 그 주소를 막는 것만으로 캡차가 무력해진다.
    public boolean verify(String token, String clientIp) {
        if (!isEnabled() || token == null || token.isBlank()) {
            return false;
        }
        String form = "secret=" + encode(secretKey) + "&response=" + encode(token)
                + (clientIp == null || clientIp.isBlank() ? "" : "&remoteip=" + encode(clientIp));
        HttpRequest httpRequest = HttpRequest.newBuilder(VERIFY_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return isSuccessResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("캡차 검증 요청 실패: {}", e.toString());
            return false;
        }
    }

    // 응답 예: {"success":true,...} / {"success":false,"error-codes":["timeout-or-duplicate"]}
    static boolean isSuccessResponse(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            return MAPPER.readTree(body).path("success").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
