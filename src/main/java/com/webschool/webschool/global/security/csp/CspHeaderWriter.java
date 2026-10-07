package com.webschool.webschool.global.security.csp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

// Content-Security-Policy 응답 헤더(보안 점검 L4, 2026-10-07). 본문을 th:utext/innerHTML로 그리는 구조라
// XSS 방어선이 HtmlSanitizer 하나뿐이었는데, 그게 뚫려도 스크립트가 실행되지 않게 하는 2차 방어선이다.
//
// 허용 출처는 지금 실제로 쓰는 것만 적었다 - 새 CDN/외부 API를 붙이면 여기도 같이 고쳐야 한다(안 고치면
// 브라우저 콘솔에 "Refused to load ..."가 뜨고 그 자원만 조용히 안 불러와진다):
// - script: 우리 서버 + nonce 붙은 인라인 + jsDelivr(Bootstrap/Quill/Chart.js). 인라인 이벤트 속성(on*)과
//   nonce 없는 <script>는 막힌다 - 템플릿에 인라인 스크립트를 추가할 땐 th:attr="nonce=${cspNonce}"를 달고,
//   on* 속성 대신 static/js/ui-actions.js의 data 속성이나 addEventListener를 쓸 것.
// - style: 인라인 허용('unsafe-inline') - Quill/템플릿이 style 속성을 많이 써서 막을 수 없고, 스타일만으로는
//   스크립트 실행이 안 돼 위험이 낮다.
// - img/media: https 전체 - 운영 업로드 파일이 S3(다른 도메인, 버킷 주소는 설정값)에 있어서.
// - connect: 우리 서버만 - JS가 외부로 직접 요청하는 곳이 없다(NEIS/날씨는 서버가 호출).
public class CspHeaderWriter implements HeaderWriter {

    private static final String HEADER = "Content-Security-Policy";
    private static final String REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    private final boolean reportOnly;

    // reportOnly=true면 차단하지 않고 브라우저 콘솔에 위반만 남긴다(app.security.csp-report-only) -
    // 배포 후 CSP 때문에 화면이 깨졌을 때 코드를 되돌리지 않고 설정만으로 끌 수 있게 둔 비상 스위치.
    public CspHeaderWriter(boolean reportOnly) {
        this.reportOnly = reportOnly;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        // 업로드 파일(/uploads/**)은 UploadResponseHeaderInterceptor가 더 강한 "sandbox"를 이미 넣었다.
        if (response.containsHeader(HEADER)) {
            return;
        }
        Object nonce = request.getAttribute(CspNonceFilter.NONCE_ATTRIBUTE);
        response.setHeader(reportOnly ? REPORT_ONLY_HEADER : HEADER, buildPolicy(nonce == null ? null : nonce.toString()));
    }

    static String buildPolicy(String nonce) {
        String scriptNonce = nonce == null ? "" : " 'nonce-" + nonce + "'";
        return String.join("; ",
                "default-src 'self'",
                "script-src 'self'" + scriptNonce + " https://cdn.jsdelivr.net",
                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com",
                "font-src 'self' data: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com",
                "img-src 'self' data: blob: https:",
                "media-src 'self' blob: https:",
                "connect-src 'self'",
                "object-src 'none'",
                "base-uri 'self'",
                "form-action 'self'",
                "frame-ancestors 'none'");
    }
}
