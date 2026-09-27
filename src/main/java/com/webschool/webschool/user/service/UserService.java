package com.webschool.webschool.user.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.mail.MailService;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 계정의 핵심(조회/회원가입)만 담당한다. 2026-09-28 파일 정리 때 나머지 책임을 분리함:
// 온보딩 게이트(학교/이메일/비밀번호 설정, 이메일 인증) → OnboardingService,
// 비로그인 계정 찾기(아이디/비밀번호) → AccountRecoveryService,
// 로그인 후 마이페이지 설정(정보 수정/프로필/알림/탈퇴) → MyPageService.
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminActionLogService adminActionLogService;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void register(RegisterDto dto) {
        UserInputValidator.requireValidUsername(dto.getUsername());

        if (dto.getPassword() == null || !dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        String email = UserInputValidator.requireValidEmail(dto.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        UserInputValidator.requireSchoolSelected(dto.getSchoolName(), dto.getSchoolCode(),
                dto.getGrade(), dto.getClassNum());

        String nickname = dto.getNickname();
        if (nickname == null || nickname.isBlank()) {
            nickname = dto.getUsername();
        }
        BannedWordFilter.validate(nickname);

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword())); // BCrypt 암호화
        user.setNickname(nickname);
        user.setEmail(email);
        user.setSchoolName(dto.getSchoolName());
        user.setSchoolCode(dto.getSchoolCode());
        user.setAtptCode(dto.getAtptCode());
        user.setSchoolKind(dto.getSchoolKind());
        user.setGrade(dto.getGrade());
        user.setClassNum(dto.getClassNum());
        user.setRole(User.Role.ROLE_USER);

        User saved = userRepository.save(user);
        // 아직 로그인 전(autoLogin은 컨트롤러에서 이후에 실행됨)이라 SecurityContext에 사용자가 없다 -
        // 5-arg 오버로드로 실제 가입자 아이디를 직접 넘긴다.
        adminActionLogService.log("USER", saved.getId(), "REGISTER",
                saved.getUsername() + " (" + saved.getSchoolName() + ")", saved.getUsername());

        sendVerification(saved);
    }

    // 이메일 인증 메일 발송 - 가입(여기), 이메일 등록/재발송(OnboardingService), 이메일 변경
    // (MyPageService)이 공용으로 쓴다. 이메일 인증은 강제 게이트가 아니라서(사용자 확정 정책)
    // 여기서 실패해도 가입/설정 자체는 그대로 성공해야 한다 - MailService가 SMTP 미설정 시 조용히
    // 스킵하지만, 혹시 모를 다른 예외까지 가입 흐름을 막지 않도록 한 번 더 방어한다.
    // public인 이유: 다른 빈이 Spring 프록시를 거쳐 호출하는데, 프록시가 가로채지 못하는
    // 가시성이면 주입 안 된 프록시 인스턴스의 필드(null)로 실행될 수 있다.
    public void sendVerification(User user) {
        try {
            String token = emailTokenService.issue(user, EmailToken.Purpose.VERIFY_EMAIL);
            mailService.sendVerificationLink(user, token);
        } catch (Exception e) {
            // 메일 발송 실패는 가입 자체를 막을 이유가 아니다 - 미인증 상태로 남고 나중에 재발송하면 됨.
        }
    }
}
