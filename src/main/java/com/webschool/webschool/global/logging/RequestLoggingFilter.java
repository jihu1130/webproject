package com.webschool.webschool.global.logging;

import com.webschool.webschool.global.util.ClientIpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// 요청 로그 + 요청 ID(2026-09-30). 모든 요청에 짧은 ID를 붙여 MDC(requestId)에 넣고 응답 헤더
// X-Request-Id로도 돌려준다 - 로그 패턴(WebschoolApplication의 기본 logging.pattern.level)에 이 값이
// 찍히므로, 한 요청 안에서 나온 서비스/에러 로그를 이 ID로 grep해서 묶어 볼 수 있다.
// 요청이 끝나면 "HTTP 메서드 경로 -> 상태 소요ms user= ip=" 한 줄을 남긴다.
//
// - 가장 먼저 실행(HIGHEST_PRECEDENCE)해야 Spring Security 안에서 나오는 로그(로그인 실패 등)에도
//   requestId가 찍힌다. 대신 이 시점엔 아직 인증 전이라 사용자 이름은 JwtAuthenticationFilter가
//   인증 성공 시 요청 속성(USER_ATTRIBUTE)에 남겨둔 값을 끝나고 읽는다(SecurityContext는 Security
//   필터가 빠져나올 때 이미 비워지므로 여기서 직접 못 읽는다).
// - 쿼리 문자열은 절대 남기지 않는다 - /reset-password?token=, /verify-email?token= 처럼 일회용
//   인증 토큰이 쿼리로 오기 때문(로그 파일만 보고 남의 비밀번호를 재설정할 수 있게 되면 안 됨).
// - 정적 리소스/헬스체크/Prometheus 스크레이프는 한 줄도 안 남기고, 로그인 사용자 전원이 20초마다
//   부르는 알림 배지 폴링(/notifications/unread-count)은 DEBUG로 낮춘다(그대로 INFO면 로그가 폴링으로
//   도배된다 - 부하 테스트 #24 참고).
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER = "user";
    public static final String USER_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".user";

    static final long SLOW_REQUEST_MS = 1000;

    // nginx 등 앞단이 이미 붙여 보낸 ID는 그대로 이어 쓴다 - 단, 로그 위조(개행 삽입 등)를 막기 위해
    // 짧은 영숫자/하이픈만 허용하고 그 외엔 새로 발급한다.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    private static final String[] SKIP_PREFIXES = {
            "/css/", "/js/", "/images/", "/uploads/", "/favicon.ico", "/actuator/",
            "/swagger-ui/", "/v3/api-docs"
    };
    private static final String POLLING_PATH = "/notifications/unread-count";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        String path = request.getRequestURI();
        boolean skip = isSkipped(path);
        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            if (!skip) {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                // 컨트롤러 밖으로 예외가 빠져나간 경우 이 시점의 status는 아직 200일 수 있다 -
                // 실제로는 컨테이너가 500을 내보내므로 그렇게 기록한다.
                int status = failed ? 500 : response.getStatus();
                logRequest(request, path, status, elapsedMs);
            }
            MDC.clear();
        }
    }

    private void logRequest(HttpServletRequest request, String path, int status, long elapsedMs) {
        Object user = request.getAttribute(USER_ATTRIBUTE);
        String userLabel = user != null ? user.toString() : "-";
        String ip = ClientIpUtils.getClientIp(request);
        String format = "HTTP {} {} -> {} {}ms user={} ip={}";
        Object[] args = {request.getMethod(), path, status, elapsedMs, userLabel, ip};

        if (status >= 500 || elapsedMs >= SLOW_REQUEST_MS) {
            log.warn(format + (elapsedMs >= SLOW_REQUEST_MS ? " (slow)" : ""), args);
        } else if (POLLING_PATH.equals(path)) {
            log.debug(format, args);
        } else {
            log.info(format, args);
        }
    }

    static String resolveRequestId(String incoming) {
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    static boolean isSkipped(String path) {
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
