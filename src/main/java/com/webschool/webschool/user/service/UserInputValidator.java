package com.webschool.webschool.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// 회원가입(UserService)/온보딩(OnboardingService)/마이페이지(MyPageService)가 똑같이 반복하던
// 아이디·이메일 형식, 학교·학년·반 선택 검증을 한 곳에 모은 것 - 원래 UserService 한 클래스에
// 있다가 서비스가 셋으로 나뉘면서(2026-09-28) 공용으로 뽑았다. 에러 메시지는 화면에 그대로
// 노출되므로 문구를 바꿀 땐 세 흐름 모두 영향을 받는다는 점에 주의.
public final class UserInputValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private UserInputValidator() {
    }

    public static void requireValidUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("아이디는 영문과 숫자만 사용할 수 있습니다.");
        }
    }

    // 앞뒤 공백을 제거한 이메일을 돌려준다 - 호출하는 쪽은 반환값을 그대로 저장하면 된다.
    public static String requireValidEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        return email;
    }

    public static final int PASSWORD_MIN_LENGTH = 8;
    // BCrypt는 72바이트까지만 반영하고(Spring Security 6.3+는 초과 시 예외) 한글은 한 글자 3바이트라 바이트로 제한한다.
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123", "passw0rd", "12345678", "123456789", "1234567890",
            "qwerty123", "qwertyuiop", "1q2w3e4r", "1q2w3e4r5t", "asdf1234", "qwer1234", "abcd1234",
            "a1234567", "iloveyou", "11111111", "00000000", "admin123", "letmein1"
    );

    // 가입/비밀번호 설정/변경/재설정 4곳 공용 비밀번호 규칙(보안 점검 M3, 2026-09-30) - 예전엔 "비어있지 않고
    // 확인란과 같은지"만 봐서 1글자 비밀번호도 가입됐다. 8자 이상 + 영문/숫자/특수문자 중 2종류 이상 +
    // 아이디와 같거나 흔한 비밀번호 금지. 기존 계정의 비밀번호는 소급하지 않는다(다음 변경 시점부터 적용).
    public static void requireValidPassword(String username, String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + PASSWORD_MIN_LENGTH + "자 이상이어야 합니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException("비밀번호가 너무 깁니다.");
        }
        int kinds = 0;
        if (password.chars().anyMatch(Character::isLetter)) kinds++;
        if (password.chars().anyMatch(Character::isDigit)) kinds++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) kinds++;
        if (kinds < 2) {
            throw new IllegalArgumentException("비밀번호는 영문, 숫자, 특수문자 중 2가지 이상을 섞어야 합니다.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (username != null && !username.isBlank() && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("비밀번호에 아이디를 포함할 수 없습니다.");
        }
        if (COMMON_PASSWORDS.contains(lower)) {
            throw new IllegalArgumentException("너무 흔한 비밀번호입니다. 다른 비밀번호를 사용해주세요.");
        }
    }

    public static void requireSchoolSelected(String schoolName, String schoolCode, String grade, String classNum) {
        if (schoolName == null || schoolName.isBlank() || schoolCode == null || schoolCode.isBlank()) {
            throw new IllegalArgumentException("목록에서 학교를 검색하여 선택해주세요.");
        }
        if (grade == null || grade.isBlank() || classNum == null || classNum.isBlank()) {
            throw new IllegalArgumentException("학년과 반을 선택해주세요.");
        }
    }
}
