package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.security.RateLimiter;
import com.webschool.webschool.global.util.ClientIpUtils;
import com.webschool.webschool.user.account.domain.EmailToken;
import com.webschool.webschool.user.account.service.AccountRecoveryService;
import com.webschool.webschool.user.account.service.EmailTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.Locale;

// 비로그인 상태의 계정 찾기 화면(아이디 찾기/비밀번호 재설정). 2026-09-28 AuthController에서 분리.
@Controller
@RequiredArgsConstructor
public class AccountRecoveryController {

    private static final int RECOVERY_LIMIT_PER_IP = 10;
    private static final int RECOVERY_LIMIT_PER_EMAIL = 3;

    private final AccountRecoveryService accountRecoveryService;
    private final EmailTokenService emailTokenService;
    private final RateLimiter rateLimiter;

    // 아이디 찾기 - 계정 존재 여부와 무관하게 항상 같은 안내를 보여준다(계정 열거 방지).
    @GetMapping("/find-username")
    public String findUsernameForm() {
        return "user/find-username";
    }

    @PostMapping("/find-username")
    public String findUsernameSubmit(@RequestParam String email, HttpServletRequest request) {
        if (!allowByIp(request)) {
            return "redirect:/find-username?limited=true";
        }
        if (allowByEmail("find-username", email)) {
            accountRecoveryService.requestUsernameReminder(email.trim());
        }
        return "redirect:/find-username?sent=true";
    }

    // 비밀번호 찾기 - 마찬가지로 계정 존재 여부와 무관하게 항상 같은 안내.
    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "user/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String email, HttpServletRequest request) {
        if (!allowByIp(request)) {
            return "redirect:/forgot-password?limited=true";
        }
        if (allowByEmail("forgot-password", email)) {
            accountRecoveryService.requestPasswordReset(email.trim());
        }
        return "redirect:/forgot-password?sent=true";
    }

    // 요청 횟수 제한(보안 점검 M4) - 두 화면 모두 요청마다 메일을 보내서, 제한이 없으면 특정 주소로
    // 메일을 계속 보내게 하거나 Gmail SMTP 일일 한도를 소진시켜 다른 사람의 인증 메일까지 막을 수 있었다.
    // IP 기준: 두 화면 합쳐 1시간 10회 - 넘으면 "잠시 후 다시" 안내(이메일과 무관한 정보라 노출돼도 괜찮음).
    private boolean allowByIp(HttpServletRequest request) {
        return rateLimiter.tryAcquireForIp("account-recovery-ip:", ClientIpUtils.getClientIp(request),
                RECOVERY_LIMIT_PER_IP, Duration.ofHours(1));
    }

    // 이메일 기준: 화면별 1시간 3회 - 넘으면 메일만 조용히 안 보내고 화면은 똑같이 "보냈다"로 보여준다
    // (다르게 보여주면 그 이메일로 최근 요청이 있었다는 정보가 새므로, 계정 열거 방지 원칙과 동일).
    private boolean allowByEmail(String purpose, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return rateLimiter.tryAcquire("account-recovery-email:" + purpose + ":" + normalized,
                RECOVERY_LIMIT_PER_EMAIL, Duration.ofHours(1));
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        try {
            emailTokenService.peek(token, EmailToken.Purpose.RESET_PASSWORD);
        } catch (IllegalArgumentException e) {
            return "redirect:/forgot-password?tokenError=true";
        }
        model.addAttribute("token", token);
        return "user/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@RequestParam String token, @RequestParam String newPassword,
                                       @RequestParam String confirmNewPassword, Model model) {
        try {
            accountRecoveryService.resetPassword(token, newPassword, confirmNewPassword);
            return "redirect:/login?reset=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("token", token);
            return "user/reset-password";
        }
    }
}
