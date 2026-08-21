package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.dto.MyPageUpdateDto;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.dto.SchoolSetupDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    private final UserDetailsService userDetailsService;
    // 구글 OAuth 클라이언트 등록(client-id/secret)이 안 돼 있으면 이 빈 자체가 없다(SecurityConfig
    // 참고) - 로그인/회원가입 화면에 "구글로 로그인" 버튼을 보여줄지 여기서 같은 방식으로 판단한다.
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("googleLoginEnabled", clientRegistrationRepositoryProvider.getIfAvailable() != null);
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
    public String deleteAccount(@RequestParam(required = false) String password,
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
            userService.setupSchool(authentication.getName(), dto);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/school-setup";
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
        model.addAttribute("googleLoginEnabled", clientRegistrationRepositoryProvider.getIfAvailable() != null);
        return "user/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterDto registerDto, HttpServletRequest request, Model model) {
        try {
            userService.register(registerDto);
            autoLogin(registerDto.getUsername(), request);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/register";
        }
    }

    // 가입 직후 바로 로그인 상태로 만들기 - 세션에 SecurityContext를 직접 심어야
    // 다음 요청(리다이렉트)부터 인증된 사용자로 인식된다(그냥 SecurityContextHolder만
    // 채우면 이번 요청 스레드에서만 유효하고 세션엔 저장되지 않는다).
    private void autoLogin(String username, HttpServletRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}