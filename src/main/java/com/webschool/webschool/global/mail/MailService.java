package com.webschool.webschool.global.mail;

import com.webschool.webschool.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// spring.mail.* 설정이 없으면 JavaMailSender 빈 자체가 없다(구글 OAuth의 ClientRegistrationRepository와
// 동일한 패턴) - 그래서 ObjectProvider로 받아서 있을 때만 실제 발송하고, 없으면 로그만 남기고 조용히
// 스킵한다. 이메일 인증은 강제 게이트가 아니므로(사용자 확정 정책) 메일이 안 나가도 가입/재설정 요청
// 자체는 항상 성공해야 한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:8888}")
    private String baseUrl;

    public void sendVerificationLink(User user, String token) {
        send(user, "[WebSchool] 이메일 인증",
                user.getNickname() + "님, 아래 링크를 눌러 이메일 인증을 완료해주세요 (24시간 이내):\n"
                        + baseUrl + "/verify-email?token=" + token);
    }

    public void sendPasswordResetLink(User user, String token) {
        send(user, "[WebSchool] 비밀번호 재설정",
                user.getNickname() + "님, 아래 링크에서 새 비밀번호를 설정해주세요 (30분 이내):\n"
                        + baseUrl + "/reset-password?token=" + token);
    }

    // 구글 계정으로 가입한 사용자가 비밀번호 재설정을 요청했을 때 - 애초에 비밀번호가 없다는 걸 안내
    public void sendGoogleAccountNotice(User user) {
        send(user, "[WebSchool] 비밀번호 재설정 안내",
                user.getNickname() + "님의 계정은 구글 로그인 전용이라 비밀번호가 없습니다. "
                        + "로그인 화면에서 \"구글 계정으로 로그인\"을 이용해주세요.");
    }

    public void sendUsernameReminder(User user) {
        send(user, "[WebSchool] 아이디 안내",
                "요청하신 WebSchool 계정의 아이디는 \"" + user.getUsername() + "\" 입니다.");
    }

    private void send(User user, String subject, String text) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("[MailService] SMTP 미설정 - 발송 스킵 (to={}, subject={})", user.getEmail(), subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
