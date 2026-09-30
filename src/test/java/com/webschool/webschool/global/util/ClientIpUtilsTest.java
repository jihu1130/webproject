package com.webschool.webschool.global.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 보안 점검 L3(2026-09-30) - X-Forwarded-For 위조로 클라이언트 IP를 바꿀 수 없어야 한다.
class ClientIpUtilsTest {

    @Test
    void behindNginx_usesRightmostAddressAddedByProxy() {
        // nginx($proxy_add_x_forwarded_for)는 클라이언트가 보낸 값 뒤에 자기가 본 주소를 덧붙인다
        assertEquals("1.2.3.4", ClientIpUtils.resolve("127.0.0.1", List.of("6.6.6.6, 1.2.3.4")));
        assertEquals("1.2.3.4", ClientIpUtils.resolve("127.0.0.1", List.of("6.6.6.6", "1.2.3.4")));
    }

    @Test
    void directConnection_ignoresHeaderEntirely() {
        assertEquals("8.8.8.8", ClientIpUtils.resolve("8.8.8.8", List.of("6.6.6.6")));
        assertEquals("172.18.0.1", ClientIpUtils.resolve("172.18.0.1", List.of("6.6.6.6")));
    }

    @Test
    void noHeaderOrGarbage_fallsBackToPeer() {
        assertEquals("127.0.0.1", ClientIpUtils.resolve("127.0.0.1", List.of()));
        assertEquals("127.0.0.1", ClientIpUtils.resolve("127.0.0.1", List.of("<script>")));
        assertEquals("127.0.0.1", ClientIpUtils.resolve("127.0.0.1", List.of("127.0.0.1")));
    }

    @Test
    void ipv6ClientIsAccepted() {
        assertEquals("2001:db8::1", ClientIpUtils.resolve("::1", List.of("2001:db8::1")));
    }

    @Test
    void trustedProxyDetection() {
        assertTrue(ClientIpUtils.isTrustedProxy("127.0.0.1"));
        assertTrue(ClientIpUtils.isTrustedProxy("0:0:0:0:0:0:0:1"));
        assertFalse(ClientIpUtils.isTrustedProxy("10.0.0.1"));
        assertFalse(ClientIpUtils.isTrustedProxy(null));
    }
}
