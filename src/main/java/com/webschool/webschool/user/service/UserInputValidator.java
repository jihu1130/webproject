package com.webschool.webschool.user.service;

import java.util.regex.Pattern;

// 회원가입(UserService)/온보딩(OnboardingService)/마이페이지(MyPageService)가 똑같이 반복하던
// 아이디·이메일 형식, 학교·학년·반 선택 검증을 한 곳에 모은 것 - 원래 UserService 한 클래스에
// 있다가 서비스가 셋으로 나뉘면서(2026-09-28) 공용으로 뽑았다. 에러 메시지는 화면에 그대로
// 노출되므로 문구를 바꿀 땐 세 흐름 모두 영향을 받는다는 점에 주의.
final class UserInputValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private UserInputValidator() {
    }

    static void requireValidUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("아이디는 영문과 숫자만 사용할 수 있습니다.");
        }
    }

    // 앞뒤 공백을 제거한 이메일을 돌려준다 - 호출하는 쪽은 반환값을 그대로 저장하면 된다.
    static String requireValidEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        return email;
    }

    static void requireSchoolSelected(String schoolName, String schoolCode, String grade, String classNum) {
        if (schoolName == null || schoolName.isBlank() || schoolCode == null || schoolCode.isBlank()) {
            throw new IllegalArgumentException("목록에서 학교를 검색하여 선택해주세요.");
        }
        if (grade == null || grade.isBlank() || classNum == null || classNum.isBlank()) {
            throw new IllegalArgumentException("학년과 반을 선택해주세요.");
        }
    }
}
