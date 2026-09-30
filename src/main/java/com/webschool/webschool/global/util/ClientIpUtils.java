package com.webschool.webschool.global.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

// 요청의 실제 클라이언트 IP - 조회수 어뷰징 방지(PostViewService), 감사 로그 IP 기록(AdminActionLogService),
// 요청 횟수 제한(RateLimiter 사용처)이 공유한다.
//
// 보안 점검 L3(2026-09-30): 예전엔 X-Forwarded-For의 "첫 번째 값"을 그대로 썼는데, 그 값은 클라이언트가
// 마음대로 넣을 수 있다(nginx는 기존 헤더 뒤에 자기가 본 주소를 덧붙이기만 한다). 그래서 헤더만 바꾸면
// 조회수 중복 방지·IP 요청 제한을 우회하고 감사 로그 IP를 위조할 수 있었다. 운영 설정
// (server.forward-headers-strategy: framework)에선 Spring의 ForwardedHeaderFilter가 같은 첫 번째 값을
// getRemoteAddr()에 덮어쓰기까지 해서, 이 유틸만 고쳐서는 안 됐다 - TrustedProxyForwardedHeaderFilter가
// 요청 맨 앞에서 resolve()로 계산한 값을 getRemoteAddr()로 돌려주도록 바꿨고, 여기서는 그 값을 읽기만 한다.
public final class ClientIpUtils {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private ClientIpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    // 신뢰하는 프록시 = 같은 서버의 nginx(운영 앱은 host 네트워크라 nginx가 127.0.0.1로 붙는다).
    // 다른 프록시(로드밸런서 등)를 앞에 두게 되면 여기에 추가할 것.
    public static boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        String v = ip.trim();
        return v.startsWith("127.") || v.equals("::1") || v.equals("0:0:0:0:0:0:0:1") || v.equals("[::1]");
    }

    // 직접 연결한 상대(peer)가 신뢰하는 프록시일 때만 X-Forwarded-For를 본다. 헤더를 오른쪽(프록시에 가까운
    // 쪽)부터 읽으며 신뢰 프록시 주소는 건너뛰고, 처음 나오는 그 외 주소가 실제 클라이언트다 - 그보다 왼쪽은
    // 클라이언트가 보낸 값이라 믿지 않는다(Tomcat RemoteIpValve와 같은 방식). IP 형식이 아닌 값을 만나면
    // 더 읽지 않고 peer를 돌려준다.
    public static String resolve(String peerAddr, List<String> forwardedForHeaders) {
        if (!isTrustedProxy(peerAddr) || forwardedForHeaders == null || forwardedForHeaders.isEmpty()) {
            return peerAddr;
        }
        List<String> hops = new ArrayList<>();
        for (String header : forwardedForHeaders) {
            if (header == null) {
                continue;
            }
            for (String part : header.split(",")) {
                if (!part.isBlank()) {
                    hops.add(part.trim());
                }
            }
        }
        Collections.reverse(hops);
        for (String hop : hops) {
            if (!looksLikeIp(hop)) {
                return peerAddr;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return peerAddr;
    }

    private static boolean looksLikeIp(String value) {
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        if (!value.contains(":") || !value.matches("^[0-9a-fA-F:.\\[\\]]+$")) {
            return false;
        }
        try {
            // 숫자/콜론만으로 이뤄진 리터럴이라 DNS 조회는 일어나지 않는다
            InetAddress.getByName(value.replace("[", "").replace("]", ""));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
