package com.webschool.webschool.global.util;

import jakarta.servlet.http.HttpServletRequest;

// 요청의 실제 클라이언트 IP를 뽑아낸다 - 조회수 어뷰징 방지(PostViewService)와 감사 로그 IP 기록
// (AdminActionLogService) 둘 다 이 유틸을 공유한다. 리버스 프록시(nginx 등) 뒤에 배포되면
// request.getRemoteAddr()는 프록시 자신의 주소만 보이므로 X-Forwarded-For를 먼저 확인한다
// (아직 이 프로젝트는 프록시 없이 직접 노출되지만, AWS 배포 로드맵의 4단계 nginx 도입을
// 미리 대비해둔다 - 그 시점에 이 유틸을 다시 손볼 필요가 없도록).
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 프록시를 여러 번 거치면 "클라이언트, 프록시1, 프록시2" 순으로 쌓이므로 맨 앞이 실제 클라이언트
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
