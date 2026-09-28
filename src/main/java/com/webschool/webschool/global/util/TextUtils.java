package com.webschool.webschool.global.util;

// 관리자 조치 로그(detail VARCHAR 300)/알림 메시지에 제목·내용을 요약해 넣을 때 쓰는 공용 헬퍼 -
// 서비스마다 복사해 쓰던 40자 자르기 로직을 모음(2026-09-28 파일 정리).
public final class TextUtils {

    private static final int DEFAULT_LIMIT = 40;

    private TextUtils() {
    }

    public static String truncate(String text) {
        return truncate(text, DEFAULT_LIMIT);
    }

    public static String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
