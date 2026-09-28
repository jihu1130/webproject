package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.dto.EmailSetupDto;
import com.webschool.webschool.user.dto.PasswordSetupDto;
import com.webschool.webschool.user.dto.SchoolSetupDto;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 직후 강제 온보딩 게이트(SchoolSetupInterceptor/EmailSetupInterceptor/
// PasswordSetupInterceptor)가 요구하는 설정들과 이메일 인증. 2026-09-28 UserService에서 분리 -
// 세 인터셉터가 이미 하나의 "온보딩" 개념으로 묶여 있던 것을 서비스로도 묶었다.
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminActionLogService adminActionLogService;
    private final EmailTokenService emailTokenService;

    // 구글 소셜 로그인 첫 가입 시 비어있는 학교/학년/반만 채우는 전용 메서드 - 아이디/비밀번호/닉네임은
    // 건드리지 않는다(그건 MyPageService.updateProfile()의 책임). SchoolSetupInterceptor가 이 정보가
    // 없는 계정을 /school-setup 화면 외에는 접근하지 못하게 막아두므로, 이 메서드가 성공해야 그 게이트가 풀린다.
    @Transactional
    public void setupSchool(String username, SchoolSetupDto dto) {
        User user = userService.getByUsername(username);

        UserInputValidator.requireSchoolSelected(dto.getSchoolName(), dto.getSchoolCode(),
                dto.getGrade(), dto.getClassNum());

        user.setSchoolName(dto.getSchoolName());
        user.setSchoolCode(dto.getSchoolCode());
        user.setAtptCode(dto.getAtptCode());
        user.setSchoolKind(dto.getSchoolKind());
        user.setGrade(dto.getGrade());
        user.setClassNum(dto.getClassNum());
        adminActionLogService.log("USER", user.getId(), "SCHOOL_SETUP", dto.getSchoolName());
    }

    // 이메일 필드가 생기기 전에 만들어진 기존 계정(admin, user1~5 등)이 다음 로그인 시
    // EmailSetupInterceptor에 의해 강제로 도착하는 화면 - 이메일만 다룬다(아이디/비번/닉네임은
    // MyPageService.updateProfile()의 책임).
    @Transactional
    public void setupEmail(String username, EmailSetupDto dto) {
        User user = userService.getByUsername(username);

        String email = UserInputValidator.requireValidEmail(dto.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        user.setEmail(email);
        user.setEmailVerified(false);
        adminActionLogService.log("USER", user.getId(), "EMAIL_SETUP", email);
        userService.sendVerification(user);
    }

    // 구글 소셜 로그인 첫 가입 시 본인도 모르는 임의 비밀번호로 시작하는 계정에 실제 비밀번호를
    // 설정하는 화면(todo.md #19) - PasswordSetupInterceptor가 needsPasswordSetup()인 계정을 여기
    // 외에는 접근하지 못하게 막아두므로, 이 메서드가 성공해야 그 게이트가 풀린다. 설정 후에도
    // provider는 GOOGLE 그대로 유지 - 로그인 방식은 여전히 구글이고, 이 비밀번호는 마이페이지에서
    // 나중에 바꿀 수 있는 로컬 비밀번호가 하나 더 생기는 것뿐이다(로그인 자체를 로컬로 전환하지 않음).
    @Transactional
    public void setupPassword(String username, PasswordSetupDto dto) {
        User user = userService.getByUsername(username);

        if (dto.getNewPassword() == null || dto.getNewPassword().isBlank()
                || !dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setPasswordSet(true);
        // 값 자체는 남기지 않는다 - MyPageService.updateProfile()의 PASSWORD_CHANGE와 동일한 이유.
        adminActionLogService.log("USER", user.getId(), "PASSWORD_SETUP", null);
    }

    @Transactional
    public void resendVerification(String username) {
        User user = userService.getByUsername(username);
        if (user.isEmailVerified() || user.needsEmailSetup()) {
            return; // 이미 인증됐거나 등록된 이메일이 없으면 보낼 것이 없음
        }
        userService.sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = emailTokenService.consume(token, EmailToken.Purpose.VERIFY_EMAIL);
        user.setEmailVerified(true);
    }
}
