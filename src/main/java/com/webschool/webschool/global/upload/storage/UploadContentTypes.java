package com.webschool.webschool.global.upload.storage;

import java.util.Locale;
import java.util.Map;

// 업로드 파일을 내려줄 때의 Content-Type을 "클라이언트가 보낸 값"이 아니라 서버가 확장자로 정한다
// (보안 점검 M1, 2026-09-30). 예전엔 S3에 file.getContentType()을 그대로 저장해서, 확장자를 .png로
// 두고 Content-Type만 text/html로 보내면 S3가 그 파일을 HTML 페이지로 서빙했다.
// 브라우저에서 바로 보여줘도 안전한 이미지/동영상만 inline이고, 그 외는 전부 application/octet-stream +
// 다운로드(attachment)로만 내려준다 - S3FileStorageService(저장 시 메타데이터)와
// UploadResponseHeaderInterceptor(로컬 /uploads/** 응답 헤더)가 같은 기준을 공유한다.
public final class UploadContentTypes {

    public static final String DOWNLOAD_ONLY_TYPE = "application/octet-stream";

    private static final Map<String, String> INLINE_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("avif", "image/avif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("m4v", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogg", "video/ogg"),
            Map.entry("mov", "video/quicktime")
    );

    private UploadContentTypes() {
    }

    public static boolean isInline(String pathOrKey) {
        return INLINE_TYPES.containsKey(extensionOf(pathOrKey));
    }

    public static String contentTypeFor(String pathOrKey) {
        return INLINE_TYPES.getOrDefault(extensionOf(pathOrKey), DOWNLOAD_ONLY_TYPE);
    }

    private static String extensionOf(String pathOrKey) {
        if (pathOrKey == null) {
            return "";
        }
        int slash = pathOrKey.lastIndexOf('/');
        int dot = pathOrKey.lastIndexOf('.');
        if (dot <= slash || dot == pathOrKey.length() - 1) {
            return "";
        }
        return pathOrKey.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
