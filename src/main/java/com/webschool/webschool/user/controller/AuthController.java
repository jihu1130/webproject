package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.dto.EmailSetupDto;
import com.webschool.webschool.user.dto.MyPageUpdateDto;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.dto.SchoolSetupDto;
import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.service.AttendanceService;
import com.webschool.webschool.user.service.EmailTokenService;
import com.webschool.webschool.user.service.MyActivityService;
import com.webschool.webschool.user.service.UserPointService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final MyActivityService myActivityService;
    private final EmailTokenService emailTokenService;
    private final AttendanceService attendanceService;
    private final UserPointService userPointService;
    // 구글 OAuth 클라이언트 등록(client-id/secret)이 안 돼 있으면 이 빈 자체가 없다(SecurityConfig
    // 참고) - 로그인/회원가입 화면에 "구글로 로그인" 버튼을 보여줄지 여기서 같은 방식으로 판단한다.
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    // 수정사항.md 지적 - 이미 로그인된 상태에서 /login·/register에 직접 들어가면(주소창 입력,
    // 즐겨찾기, 뒤로가기) 네비바는 로그인 상태를 보여주면서 그 아래에 로그인/회원가입 폼이
    // 또 뜨는 문제가 있었다. 인증된 사용자는 두 페이지 모두 홈으로 돌려보낸다.
    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    @GetMapping("/login")
    public String loginPage(Model model, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("googleLoginEnabled", clientRegistrationRepositoryProvider.getIfAvailable() != null);
        return "user/login";
    }

    @GetMapping("/mypage")
    public String myPage(Authentication authentication, Model model) {
        // 프로필 카드 통계 바(게시글/댓글/받은 좋아요) - 프로필_디자인.md 설계 반영.
        model.addAttribute("stats", myActivityService.getStats(authentication.getName()));
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("attendanceCheckedInToday", attendanceService.hasCheckedInToday(user.getId()));
        return "user/mypage";
    }

    // 출석체크(todo.md 요구사항) - 매일 방문 시 기본 포인트 지급. 하루 한 번만 지급되며,
    // 이미 체크인했으면 checkIn()이 조용히 아무 것도 하지 않는다.
    @PostMapping("/mypage/attendance")
    public String checkInAttendance(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        boolean checkedIn = attendanceService.checkIn(user);
        return "redirect:/mypage?attendance=" + (checkedIn ? "success" : "already");
    }

    // 포인트 내역 화면(todo.md 요구사항) - 적립/소비 이력을 최신순으로 보여준다.
    @GetMapping("/mypage/points")
    public String pointHistory(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(required = false) Integer size,
                                Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("logs", userPointService.getHistory(user.getId(), page, PageUtils.normalizeSize(size)));
        return "user/point-history";
    }

    @GetMapping("/mypage/edit")
    public String myPageEditForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());

        MyPageUpdateDto dto = new MyPageUpdateDto();
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
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
            dto.setEmail(user.getEmail());
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

    // 프로필 사진 업로드/되돌리기 - 소개글(/mypage/profile)과 같은 "남이 보는 내 프로필" 설정
    // 화면 안에 있지만, 파일 업로드라 별도 엔드포인트로 분리했다(폼이 섞이면 사진만 실패했을 때
    // 방금 입력한 소개글까지 같이 날아가 보이는 게 어색해서).
    @PostMapping("/mypage/profile/image")
    public String myProfileImageSubmit(@RequestParam("profileImage") MultipartFile profileImage,
                                        Authentication authentication, Model model) {
        try {
            userService.updateProfileImage(authentication.getName(), profileImage);
            return "redirect:/mypage/profile?updated=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("bio", userService.getByUsername(authentication.getName()).getBio());
            return "user/profile-edit";
        }
    }

    @PostMapping("/mypage/profile/image/reset")
    public String myProfileImageReset(Authentication authentication) {
        userService.resetProfileImage(authentication.getName());
        return "redirect:/mypage/profile?updated=true";
    }

    // 알림 설정 - "남에게 보이는 내 정보"(프로필/계정 정보)와는 성격이 다른 "내가 받을 알림"
    // 설정이라 별도 화면으로 분리했다(위 /mypage/profile, /mypage/edit과 동일한 분리 관례).
    @GetMapping("/mypage/notifications")
    public String notificationSettingsForm(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("contestDeadlineAlertEnabled", user.isContestDeadlineAlertEnabled());
        model.addAttribute("commentAlertEnabled", user.isCommentAlertEnabled());
        model.addAttribute("likeAlertEnabled", user.isLikeAlertEnabled());
        model.addAttribute("replyAlertEnabled", user.isReplyAlertEnabled());
        return "user/notification-settings";
    }

    @PostMapping("/mypage/notifications")
    public String notificationSettingsSubmit(@RequestParam(defaultValue = "false") boolean contestDeadlineAlertEnabled,
                                              @RequestParam(defaultValue = "false") boolean commentAlertEnabled,
                                              @RequestParam(defaultValue = "false") boolean likeAlertEnabled,
                                              @RequestParam(defaultValue = "false") boolean replyAlertEnabled,
                                              Authentication authentication) {
        userService.updateNotificationPreferences(authentication.getName(), contestDeadlineAlertEnabled,
                commentAlertEnabled, likeAlertEnabled, replyAlertEnabled);
        return "redirect:/mypage/notifications?updated=true";
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
            userService.setupEmail(authentication.getName(), dto);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "user/email-setup";
        }
    }

    // 이메일 인증 링크 - 로그인 여부와 무관하게 동작(다른 기기에서 열 수도 있음).
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Authentication authentication) {
        String target = isAuthenticated(authentication) ? "/mypage" : "/login";
        try {
            userService.verifyEmail(token);
            return "redirect:" + target + "?verified=true";
        } catch (IllegalArgumentException e) {
            return "redirect:" + target + "?verifyError=true";
        }
    }

    @PostMapping("/mypage/resend-verification")
    public String resendVerification(Authentication authentication) {
        userService.resendVerification(authentication.getName());
        return "redirect:/mypage?resent=true";
    }

    // 아이디 찾기 - 계정 존재 여부와 무관하게 항상 같은 안내를 보여준다(계정 열거 방지).
    @GetMapping("/find-username")
    public String findUsernameForm() {
        return "user/find-username";
    }

    @PostMapping("/find-username")
    public String findUsernameSubmit(@RequestParam String email) {
        userService.requestUsernameReminder(email.trim());
        return "redirect:/find-username?sent=true";
    }

    // 비밀번호 찾기 - 마찬가지로 계정 존재 여부와 무관하게 항상 같은 안내.
    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "user/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String email) {
        userService.requestPasswordReset(email.trim());
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
            userService.resetPassword(token, newPassword, confirmNewPassword);
            return "redirect:/login?reset=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("token", token);
            return "user/reset-password";
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
    public String registerPage(Model model, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
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