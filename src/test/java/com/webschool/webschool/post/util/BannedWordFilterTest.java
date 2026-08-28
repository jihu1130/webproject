package com.webschool.webschool.post.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BannedWordFilterTest {

    @Test
    void nullAndCleanTextPass() {
        assertDoesNotThrow(() -> BannedWordFilter.validate(null));
        assertDoesNotThrow(() -> BannedWordFilter.validate("오늘 급식 뭐 나와요?"));
    }

    @Test
    void plainBannedWordIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BannedWordFilter.validate("이 씨발 진짜"));
    }

    @Test
    void whitespaceInsertedEvasionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BannedWordFilter.validate("시 발 놈아"));
    }

    @Test
    void specialCharacterInsertedEvasionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BannedWordFilter.validate("시.발_새끼"));
    }

    @Test
    void repeatedCharacterEvasionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BannedWordFilter.validate("씨씨발발"));
    }

    @Test
    void englishBannedWordIsCaseInsensitive() {
        assertThrows(IllegalArgumentException.class, () -> BannedWordFilter.validate("you FUCK off"));
    }

    @Test
    void falsePositiveLikeSubstringStillMatchesKnownLimitation() {
        // "시발음"(poem pronunciation)처럼 정상 단어가 우연히 금지어를 부분 문자열로 포함하는 경우도
        // 걸러진다 - 단순 포함 검사의 알려진 한계이며 새 정규화로 해결되는 문제가 아니다(회귀 테스트
        // 목적이 아니라 이 한계를 명시적으로 기록해두는 테스트).
        assertThrows(IllegalArgumentException.class, () -> BannedWordFilter.validate("이 시발음이 맞나요"));
    }
}
