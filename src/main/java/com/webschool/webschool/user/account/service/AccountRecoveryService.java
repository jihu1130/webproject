package com.webschool.webschool.user.account.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.mail.MailService;
import com.webschool.webschool.user.account.domain.EmailToken;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.service.UserInputValidator;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 비로그인 상태의 계정 찾기(아이디 찾기/비밀번호 재설정). 2026-09-28 UserService에서 분리.
// 계정 열거 방지를 위해 이메일이 존재하지 않아도 실패를 드러내지 않는 게 이 흐름의 공통 원칙이다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminActionLogService adminActionLogService;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;

    // 이메일 존재 여부와 무관하게 항상 같은 결과로 보이도록(계정 열거 방지), 실제 매칭 실패는
    // 여기서 조용히 무시하고 컨트롤러는 이 메서드 성공/실패와 상관없이 같은 안내 문구를 보여준다.
    @Transactional
    public void requestPasswordReset(String email) {
        // 입력한 이메일 주소와 발급 토큰은 로그에 남기지 않는다(개인정보/재설정 링크 탈취 방지).
        // 화면에는 계정 존재 여부를 숨기지만(아이디 수집 방지) 서버 로그에는 결과를 남겨 둔다.
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            if (user.getProvider() == User.Provider.GOOGLE) {
                mailService.sendGoogleAccountNotice(user);
                log.info("비밀번호 재설정 요청 - 구글 계정이라 안내 메일만 발송 userId={}", user.getId());
                return;
            }
            String token = emailTokenService.issue(user, EmailToken.Purpose.RESET_PASSWORD);
            mailService.sendPasswordResetLink(user, token);
            log.info("비밀번호 재설정 요청 - 링크 발송 userId={}", user.getId());
        }, () -> log.info("비밀번호 재설정 요청 - 일치하는 계정 없음"));
    }

    @Transactional
    public void requestUsernameReminder(String email) {
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            mailService.sendUsernameReminder(user);
            log.info("아이디 찾기 요청 - 안내 메일 발송 userId={}", user.getId());
        }, () -> log.info("아이디 찾기 요청 - 일치하는 계정 없음"));
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmNewPassword) {
        if (newPassword == null || newPassword.isBlank() || !newPassword.equals(confirmNewPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        // 규칙 위반이면 토큰을 소비하기 전에 거절한다 - 먼저 consume()하면 메일 링크가 1회용이라
        // 비밀번호만 다시 고쳐 입력해도 "유효하지 않은 링크"가 돼버린다.
        UserInputValidator.requireValidPassword(
                emailTokenService.peek(token, EmailToken.Purpose.RESET_PASSWORD).getUsername(), newPassword);
        User user = emailTokenService.consume(token, EmailToken.Purpose.RESET_PASSWORD);
        user.setPassword(passwordEncoder.encode(newPassword));
        // 비로그인 상태에서 일어나는 조치라 actorUsername을 직접 넘긴다(UserService.register()와 동일한 이유).
        adminActionLogService.log("USER", user.getId(), "PASSWORD_RESET", null, user.getUsername());
    }
}
