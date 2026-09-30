package com.webschool.webschool.global.upload.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 로컬 저장소 모드(/uploads/**, 앱과 같은 오리진)에서 업로드 파일이 페이지처럼 실행되지 않게 막는
// 응답 헤더(보안 점검 M1, 2026-09-30). 확장자 허용 목록(FileUploadService)을 우회한 파일이나,
// 허용 목록 도입 전에 이미 올라가 있던 파일(.html 등)까지 여기서 한 번 더 막는다.
// - 모든 파일: CSP sandbox - 문서로 열려도 스크립트 실행/같은 오리진 권한이 없다(<img>/<video>로
//   불러오는 이미지·영상에는 영향 없음).
// - 이미지/동영상이 아닌 파일: attachment - 브라우저가 열지 않고 다운로드만 한다.
@Component
public class UploadResponseHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Content-Security-Policy", "sandbox");
        if (!UploadContentTypes.isInline(request.getRequestURI())) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        }
        return true;
    }
}
