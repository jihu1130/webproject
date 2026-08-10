package com.webschool.webschool.post.util;

import java.util.List;

/**
 * 게시글/댓글 등록 시 금지어 포함 여부를 검사하는 간단한 정적 필터.
 * 목록은 최소한의 예시일 뿐이므로 운영 시 확장이 필요하다.
 */
public final class BannedWordFilter {

    private static final List<String> BANNED_WORDS = List.of(
            "시발", "씨발", "개새끼", "병신", "좆"
    );

    private BannedWordFilter() {
    }

    public static void validate(String text) {
        if (text == null) {
            return;
        }
        String normalized = text.replaceAll("\\s+", "");
        for (String word : BANNED_WORDS) {
            if (normalized.contains(word)) {
                throw new IllegalArgumentException("금지어가 포함되어 있어 등록할 수 없습니다.");
            }
        }
    }
}
