package com.webschool.webschool.user.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 보안 점검 L5(2026-10-07) - 가입/아이디 변경 공용 아이디 규칙 회귀 테스트.
class UserInputValidatorUsernameTest {

    // anonymousUser는 Spring Security가 비로그인 사용자에게 붙이는 이름이라 실제 계정이 되면 안 된다
    // (AuthenticationUtils/SchoolSetupInterceptor가 이 문자열로 비로그인을 판별) - 대소문자를 바꿔도 막는다.
    @ParameterizedTest
    @ValueSource(strings = {"anonymousUser", "ANONYMOUSUSER", "anonymoususer", "system", "Root", "administrator"})
    void reservedUsernamesAreRejected(String username) {
        assertThrows(IllegalArgumentException.class, () -> UserInputValidator.requireValidUsername(username));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abc", "abcdefghij12345678901", "한글아이디", "with space", "under_score", ""})
    void wrongLengthOrCharactersAreRejected(String username) {
        assertThrows(IllegalArgumentException.class, () -> UserInputValidator.requireValidUsername(username));
    }

    // admin/subadmin/test1은 개발용 시더(TestDataSeeder)가 일반 가입 경로로 만드는 계정이라 통과해야 한다.
    @ParameterizedTest
    @ValueSource(strings = {"test1", "admin", "subadmin", "minseo2026", "abcd", "abcdefghij1234567890"})
    void ordinaryUsernamesPass(String username) {
        assertDoesNotThrow(() -> UserInputValidator.requireValidUsername(username));
    }
}
