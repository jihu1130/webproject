package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.dto.MyPageUpdateDto;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "user/login";
    }

    @GetMapping("/mypage")
    public String myPage() {
        return "user/mypage";
    }

    @GetMapping("/mypage/edit")
    public String myPageEditForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());

        MyPageUpdateDto dto = new MyPageUpdateDto();
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setSchoolName(user.getSchoolName());
        dto.setSchoolCode(user.getSchoolCode());
        dto.setAtptCode(user.getAtptCode());
        dto.setSchoolKind(user.getSchoolKind());
        dto.setGrade(user.getGrade());
        dto.setClassNum(user.getClassNum());

        model.addAttribute("updateDto", dto);
        return "user/mypage-edit";
    }

    @PostMapping("/mypage/edit")
    public String myPageEditSubmit(@ModelAttribute("updateDto") MyPageUpdateDto dto,
                                    Authentication authentication,
                                    HttpServletRequest request, HttpServletResponse response,
                                    Model model) {
        try {
            boolean usernameChanged = userService.updateProfile(authentication.getName(), dto);
            if (usernameChanged) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                return "redirect:/login?updated=true";
            }
            return "redirect:/mypage?updated=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/mypage-edit";
        }
    }

    @PostMapping("/mypage/delete")
    public String deleteAccount(@RequestParam String password,
                                 Authentication authentication,
                                 HttpServletRequest request, HttpServletResponse response,
                                 Model model) {
        try {
            userService.deleteAccount(authentication.getName(), password);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/login?accountDeleted=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            User user = userService.getByUsername(authentication.getName());
            MyPageUpdateDto dto = new MyPageUpdateDto();
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname());
            dto.setSchoolName(user.getSchoolName());
            dto.setSchoolCode(user.getSchoolCode());
            dto.setAtptCode(user.getAtptCode());
            dto.setSchoolKind(user.getSchoolKind());
            dto.setGrade(user.getGrade());
            dto.setClassNum(user.getClassNum());
            model.addAttribute("updateDto", dto);
            return "user/mypage-edit";
        }
    }

    // "내 프로필 설정" - 남이 보는 프로필(/users/{id})에 노출되는 소개글 전용 수정 화면.
    // 아이디/비밀번호/학교 정보를 다루는 "내 정보 수정"(/mypage/edit)과는 목적이 달라서 분리했다.
    @GetMapping("/mypage/profile")
    public String myProfileSettingsForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("bio", user.getBio());
        return "user/profile-edit";
    }

    @PostMapping("/mypage/profile")
    public String myProfileSettingsSubmit(@RequestParam(required = false) String bio,
                                           Authentication authentication, Model model) {
        try {
            userService.updateBio(authentication.getName(), bio);
            return "redirect:/mypage?updated=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("bio", bio);
            return "user/profile-edit";
        }
    }

    // 아이디 중복확인 API
    @GetMapping("/api/users/check-username")
    @ResponseBody
    public Map<String, Object> checkUsername(@RequestParam(required = false) String username) {
        if (username == null || username.isBlank()) {
            return Map.of("available", false, "message", "아이디를 입력해주세요.");
        }

        boolean available = userService.isUsernameAvailable(username);
        return Map.of(
                "available", available,
                "message", available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."
        );
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerDto", new RegisterDto());
        return "user/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterDto registerDto, Model model) {
        try {
            userService.register(registerDto);
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/register";
        }
    }
}