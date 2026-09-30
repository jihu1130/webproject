package com.webschool.webschool.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 보안 점검 M3(2026-09-30) - 가입/설정/변경/재설정 공용 비밀번호 규칙 회귀 테스트.
class UserInputValidatorPasswordTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "abc123", "abcdefgh", "12345678", "!!!!!!!!", "password1", "1q2w3e4r"})
    void weakPasswordsAreRejected(String password) {
        assertThrows(IllegalArgumentException.class, () -> UserInputValidator.requireValidPassword("minseo", password));
    }

    @Test
    void passwordContainingUsernameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> UserInputValidator.requireValidPassword("test1", "test1!"));
        assertThrows(IllegalArgumentException.class, () -> UserInputValidator.requireValidPassword("Test1", "xxTEST1yy9"));
    }

    @Test
    void tooLongForBcryptIsRejected() {
        // 한글 25자 = 75바이트 > BCrypt 72바이트 한도
        assertThrows(IllegalArgumentException.class,
                () -> UserInputValidator.requireValidPassword("minseo", "가".repeat(25) + "1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"school2026", "Lunch!time", "급식맛있다2026"})
    void reasonablePasswordsPass(String password) {
        assertDoesNotThrow(() -> UserInputValidator.requireValidPassword("minseo", password));
    }
}
