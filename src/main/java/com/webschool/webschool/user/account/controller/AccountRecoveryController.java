package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.user.account.domain.EmailToken;
import com.webschool.webschool.user.account.service.AccountRecoveryService;
import com.webschool.webschool.user.account.service.EmailTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 비로그인 상태의 계정 찾기 화면(아이디 찾기/비밀번호 재설정). 2026-09-28 AuthController에서 분리.
@Controller
@RequiredArgsConstructor
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;
    private final EmailTokenService emailTokenService;

    // 아이디 찾기 - 계정 존재 여부와 무관하게 항상 같은 안내를 보여준다(계정 열거 방지).
    @GetMapping("/find-username")
    public String findUsernameForm() {
        return "user/find-username";
    }

    @PostMapping("/find-username")
    public String findUsernameSubmit(@RequestParam String email) {
        accountRecoveryService.requestUsernameReminder(email.trim());
        return "redirect:/find-username?sent=true";
    }

    // 비밀번호 찾기 - 마찬가지로 계정 존재 여부와 무관하게 항상 같은 안내.
    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "user/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String email) {
        accountRecoveryService.requestPasswordReset(email.trim());
        return "redirect:/forgot-password?sent=true";
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
