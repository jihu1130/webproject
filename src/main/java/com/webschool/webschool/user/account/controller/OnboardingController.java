package com.webschool.webschool.user.account.controller;

import com.webschool.webschool.global.security.AuthenticationUtils;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.account.dto.EmailSetupDto;
import com.webschool.webschool.user.account.dto.PasswordSetupDto;
import com.webschool.webschool.user.account.dto.SchoolSetupDto;
import com.webschool.webschool.user.account.service.OnboardingService;
import com.webschool.webschool.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 로그인 직후 강제 온보딩 화면(SchoolSetupInterceptor/EmailSetupInterceptor/
// PasswordSetupInterceptor가 보내는 곳)과 이메일 인증. 2026-09-28 AuthController에서 분리.
// 새 온보딩 화면을 추가하면 세 인터셉터의 ALLOWED_PATHS에도 그 경로를 넣을 것(CLAUDE.md 함정 참고).
@Controller
@RequiredArgsConstructor
public class OnboardingController {

    private final UserService userService;
    private final OnboardingService onboardingService;

    // 구글 소셜 로그인 첫 가입 시 비어있는 학교/학년/반을 채우는 화면 - SchoolSetupInterceptor가
    // 이 정보가 없는 계정을 여기 외에는 접근하지 못하게 강제로 리다이렉트한다.
    @GetMapping("/school-setup")
    public String schoolSetupForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());

        SchoolSetupDto dto = new SchoolSetupDto();
        dto.setSchoolName(user.getSchoolName());
        dto.setSchoolCode(user.getSchoolCode());
        dto.setAtptCode(user.getAtptCode());
        dto.setSchoolKind(user.getSchoolKind());
        dto.setGrade(user.getGrade());
        dto.setClassNum(user.getClassNum());

        model.addAttribute("setupDto", dto);
        return "user/school-setup";
    }

    @PostMapping("/school-setup")
    public String schoolSetupSubmit(@ModelAttribute("setupDto") SchoolSetupDto dto,
                                     Authentication authentication, Model model) {
        try {
            onboardingService.setupSchool(authentication.getName(), dto);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/school-setup";
        }
    }

    // 이메일 필드가 생기기 전에 만들어진 기존 계정이 다음 로그인 시 EmailSetupInterceptor에 의해
    // 강제로 도착하는 화면.
    @GetMapping("/email-setup")
    public String emailSetupForm(Model model) {
        model.addAttribute("setupDto", new EmailSetupDto());
        return "user/email-setup";
    }

    @PostMapping("/email-setup")
    public String emailSetupSubmit(@ModelAttribute("setupDto") EmailSetupDto dto,
                                    Authentication authentication, Model model) {
        try {
            onboardingService.setupEmail(authentication.getName(), dto);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/email-setup";
        }
    }

    // 구글 소셜 로그인 첫 가입 시 본인도 모르는 임의 비밀번호로 시작하는 계정에 실제 비밀번호를
    // 설정하는 화면(todo.md #19) - PasswordSetupInterceptor가 이 설정을 마치지 못한 구글 계정을
    // 여기 외에는 접근하지 못하게 강제로 리다이렉트한다.
    @GetMapping("/password-setup")
    public String passwordSetupForm(Model model) {
        model.addAttribute("setupDto", new PasswordSetupDto());
        return "user/password-setup";
    }

    @PostMapping("/password-setup")
    public String passwordSetupSubmit(@ModelAttribute("setupDto") PasswordSetupDto dto,
                                       Authentication authentication, Model model) {
        try {
            onboardingService.setupPassword(authentication.getName(), dto);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/password-setup";
        }
    }

    // 이메일 인증 링크 - 로그인 여부와 무관하게 동작(다른 기기에서 열 수도 있음).
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Authentication authentication) {
        String target = AuthenticationUtils.isLoggedIn(authentication) ? "/mypage" : "/login";
        try {
            onboardingService.verifyEmail(token);
            return "redirect:" + target + "?verified=true";
        } catch (IllegalArgumentException e) {
            return "redirect:" + target + "?verifyError=true";
        }
    }

    @PostMapping("/mypage/resend-verification")
    public String resendVerification(Authentication authentication) {
        onboardingService.resendVerification(authentication.getName());
        return "redirect:/mypage?resent=true";
    }
}
