package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.EmailTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private final EmailTokenRepository emailTokenRepository;

    // 이메일 인증은 링크를 며칠 늦게 눌러도 큰 문제가 없지만, 비밀번호 재설정은 탈취 위험이 있는
    // 링크라 짧게 잡는다.
    private static final java.util.Map<EmailToken.Purpose, java.time.Duration> TTL = java.util.Map.of(
            EmailToken.Purpose.VERIFY_EMAIL, java.time.Duration.ofHours(24),
            EmailToken.Purpose.RESET_PASSWORD, java.time.Duration.ofMinutes(30)
    );

    @Transactional
    public String issue(User user, EmailToken.Purpose purpose) {
        // 같은 용도로 이미 발급된 미사용 토큰이 있으면 지워서, 새로 요청한 링크만 유효하게 만든다
        // (옛 메일에 남아있는 링크가 계속 살아있으면 안 됨).
        emailTokenRepository.deleteByUserAndPurpose(user, purpose);

        EmailToken emailToken = new EmailToken();
        emailToken.setUser(user);
        emailToken.setToken(UUID.randomUUID().toString());
        emailToken.setPurpose(purpose);
        emailToken.setExpiresAt(LocalDateTime.now().plus(TTL.get(purpose)));
        emailTokenRepository.save(emailToken);
        return emailToken.getToken();
    }

    // 폼을 렌더링하기 전 토큰이 아직 유효한지만 확인 - 소비(used=true)는 하지 않는다.
    @Transactional(readOnly = true)
    public User peek(String token, EmailToken.Purpose purpose) {
        return resolve(token, purpose).getUser();
    }

    @Transactional
    public User consume(String token, EmailToken.Purpose purpose) {
        EmailToken emailToken = resolve(token, purpose);
        emailToken.setUsed(true);
        return emailToken.getUser();
    }

    private EmailToken resolve(String token, EmailToken.Purpose purpose) {
        EmailToken emailToken = emailTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 링크입니다."));
        if (emailToken.getPurpose() != purpose || emailToken.isUsed()) {
            throw new IllegalArgumentException("유효하지 않은 링크입니다.");
        }
        if (emailToken.isExpired()) {
            throw new IllegalArgumentException("링크가 만료되었습니다. 다시 요청해주세요.");
        }
        return emailToken;
    }
}
