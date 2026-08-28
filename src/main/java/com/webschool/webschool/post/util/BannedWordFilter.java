package com.webschool.webschool.post.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 게시글/댓글/한마디/닉네임 등록 시 금지어 포함 여부를 검사하는 정적 필터.
 * 자모 분리처럼 정교한 우회까지 막는 게 목표는 아니고, 공백·특수문자 삽입·문자
 * 반복처럼 흔한 우회 패턴만 정규화해서 걸러낸다. 목록도 확장했지만 여전히
 * 완전한 목록은 아니므로 운영 중 신고/모니터링으로 계속 보강할 것.
 */
public final class BannedWordFilter {

    private static final List<String> BANNED_WORDS = List.of(
            "시발", "씨발", "씨팔", "시팔", "개새끼", "개새키", "병신", "병신새끼",
            "좆", "좇", "지랄", "느금마", "니애미", "미친놈", "미친년", "개자식",
            "썅년", "썅놈", "fuck", "motherfucker"
    );

    // 한글 음절/자모, 영문, 숫자만 남기고 나머지(공백/구두점/특수기호/제로폭문자 등)는
    // 전부 제거한다 - "시.발"/"시 발"/"시_발"처럼 특수문자·공백을 끼워넣는 우회를
    // 하나의 정규화 단계로 막는다.
    private static final Pattern NON_MEANINGFUL = Pattern.compile("[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]");

    private BannedWordFilter() {
    }

    public static void validate(String text) {
        if (text == null) {
            return;
        }
        String normalized = normalize(text);
        for (String word : BANNED_WORDS) {
            if (normalized.contains(word)) {
                throw new IllegalArgumentException("금지어가 포함되어 있어 등록할 수 없습니다.");
            }
        }
    }

    // 특수문자/공백 제거 후 소문자화(영문 대소문자 우회 방지)하고, "씨씨발발"처럼 같은
    // 글자를 반복해서 끼워넣는 우회를 막기 위해 연속된 동일 문자를 하나로 축약한다.
    // 축약이 안전한 이유: 현재 BANNED_WORDS 어떤 항목도 내부에 같은 문자가 연속으로
    // 오지 않아서, 이 정규화가 정상 매칭을 깨뜨리지 않는다.
    private static String normalize(String text) {
        String stripped = NON_MEANINGFUL.matcher(text).replaceAll("").toLowerCase();
        StringBuilder collapsed = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (collapsed.length() == 0 || c != prev) {
                collapsed.append(c);
            }
            prev = c;
        }
        return collapsed.toString();
    }
}
