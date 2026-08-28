package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.mail.MailService;
import com.webschool.webschool.global.upload.FileUploadService;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.EmailToken;
import com.webschool.webschool.user.dto.EmailSetupDto;
import com.webschool.webschool.user.dto.MyPageUpdateDto;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.dto.SchoolSetupDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminActionLogService adminActionLogService;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;
    private final FileUploadService fileUploadService;

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public void register(RegisterDto dto) {
        if (dto.getUsername() == null || !USERNAME_PATTERN.matcher(dto.getUsername()).matches()) {
            throw new IllegalArgumentException("아이디는 영문과 숫자만 사용할 수 있습니다.");
        }

        if (dto.getPassword() == null || !dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        String email = dto.getEmail() == null ? "" : dto.getEmail().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (dto.getSchoolName() == null || dto.getSchoolName().isBlank()
                || dto.getSchoolCode() == null || dto.getSchoolCode().isBlank()) {
            throw new IllegalArgumentException("목록에서 학교를 검색하여 선택해주세요.");
        }

        if (dto.getGrade() == null || dto.getGrade().isBlank()
                || dto.getClassNum() == null || dto.getClassNum().isBlank()) {
            throw new IllegalArgumentException("학년과 반을 선택해주세요.");
        }

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

    // 이메일 인증은 강제 게이트가 아니라서(사용자 확정 정책) 여기서 실패해도 가입/설정 자체는
    // 그대로 성공해야 한다 - MailService가 SMTP 미설정 시 조용히 스킵하지만, 혹시 모를 다른 예외까지
    // 가입 흐름을 막지 않도록 한 번 더 방어한다.
    private void sendVerification(User user) {
        try {
            String token = emailTokenService.issue(user, EmailToken.Purpose.VERIFY_EMAIL);
            mailService.sendVerificationLink(user, token);
        } catch (Exception e) {
            // 메일 발송 실패는 가입 자체를 막을 이유가 아니다 - 미인증 상태로 남고 나중에 재발송하면 됨.
        }
    }

    /**
     * 마이페이지 정보 수정. 아이디가 변경되면 true를 반환 (세션 재로그인 필요).
     */
    @Transactional
    public boolean updateProfile(String currentUsername, MyPageUpdateDto dto) {
        User user = getByUsername(currentUsername);

        // 소셜 로그인(GOOGLE) 계정은 본인도 모르는 임의 비밀번호가 들어있어(User.password 필드 주석
        // 참고) 현재 비밀번호 확인 자체가 성립하지 않는다 - LOCAL 계정에만 이 확인을 요구한다.
        if (user.getProvider() == User.Provider.LOCAL) {
            if (dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()
                    || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }
        }

        if (dto.getSchoolName() == null || dto.getSchoolName().isBlank()
                || dto.getSchoolCode() == null || dto.getSchoolCode().isBlank()) {
            throw new IllegalArgumentException("목록에서 학교를 검색하여 선택해주세요.");
        }

        if (dto.getGrade() == null || dto.getGrade().isBlank()
                || dto.getClassNum() == null || dto.getClassNum().isBlank()) {
            throw new IllegalArgumentException("학년과 반을 선택해주세요.");
        }

        boolean usernameChanged = false;
        String newUsername = dto.getUsername() == null ? "" : dto.getUsername().trim();

        if (!newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
                throw new IllegalArgumentException("아이디는 영문과 숫자만 사용할 수 있습니다.");
            }
            if (userRepository.existsByUsername(newUsername)) {
                throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
            }
            String oldUsername = user.getUsername();
            user.setUsername(newUsername);
            usernameChanged = true;
            adminActionLogService.log("USER", user.getId(), "USERNAME_CHANGE", oldUsername + " -> " + newUsername);
        }

        if (dto.getNewPassword() != null && !dto.getNewPassword().isBlank()) {
            if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
                throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다.");
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            // 값 자체(원문/해시 불문)는 절대 detail에 남기지 않는다 - 변경이 일어났다는 사실만 기록.
            adminActionLogService.log("USER", user.getId(), "PASSWORD_CHANGE", null);
        }

        String nickname = dto.getNickname();
        String resolvedNickname = (nickname == null || nickname.isBlank()) ? user.getUsername() : nickname.trim();
        BannedWordFilter.validate(resolvedNickname);
        user.setNickname(resolvedNickname);

        String newEmail = dto.getEmail() == null ? "" : dto.getEmail().trim();
        if (!EMAIL_PATTERN.matcher(newEmail).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        if (!newEmail.equals(user.getEmail())) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
            }
            user.setEmail(newEmail);
            user.setEmailVerified(false); // 이메일이 바뀌었으니 새 주소로 다시 인증해야 함
            sendVerification(user);
            adminActionLogService.log("USER", user.getId(), "EMAIL_CHANGE", newEmail);
        }

        user.setSchoolName(dto.getSchoolName());
        user.setSchoolCode(dto.getSchoolCode());
        user.setAtptCode(dto.getAtptCode());
        user.setSchoolKind(dto.getSchoolKind());
        user.setGrade(dto.getGrade());
        user.setClassNum(dto.getClassNum());

        return usernameChanged;
    }

    // 구글 소셜 로그인 첫 가입 시 비어있는 학교/학년/반만 채우는 전용 메서드 - 아이디/비밀번호/닉네임은
    // 건드리지 않는다(그건 updateProfile()의 책임). SchoolSetupInterceptor가 이 정보가 없는 계정을
    // /school-setup 화면 외에는 접근하지 못하게 막아두므로, 이 메서드가 성공해야 그 게이트가 풀린다.
    @Transactional
    public void setupSchool(String username, SchoolSetupDto dto) {
        User user = getByUsername(username);

        if (dto.getSchoolName() == null || dto.getSchoolName().isBlank()
                || dto.getSchoolCode() == null || dto.getSchoolCode().isBlank()) {
            throw new IllegalArgumentException("목록에서 학교를 검색하여 선택해주세요.");
        }

        if (dto.getGrade() == null || dto.getGrade().isBlank()
                || dto.getClassNum() == null || dto.getClassNum().isBlank()) {
            throw new IllegalArgumentException("학년과 반을 선택해주세요.");
        }

        user.setSchoolName(dto.getSchoolName());
        user.setSchoolCode(dto.getSchoolCode());
        user.setAtptCode(dto.getAtptCode());
        user.setSchoolKind(dto.getSchoolKind());
        user.setGrade(dto.getGrade());
        user.setClassNum(dto.getClassNum());
        adminActionLogService.log("USER", user.getId(), "SCHOOL_SETUP", dto.getSchoolName());
    }

    // 이메일 필드가 생기기 전에 만들어진 기존 계정(admin, user1~5 등)이 다음 로그인 시
    // EmailSetupInterceptor에 의해 강제로 도착하는 화면 - 이메일만 다룬다(아이디/비번/닉네임은
    // updateProfile()의 책임).
    @Transactional
    public void setupEmail(String username, EmailSetupDto dto) {
        User user = getByUsername(username);

        String email = dto.getEmail() == null ? "" : dto.getEmail().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        user.setEmail(email);
        user.setEmailVerified(false);
        adminActionLogService.log("USER", user.getId(), "EMAIL_SETUP", email);
        sendVerification(user);
    }

    @Transactional
    public void resendVerification(String username) {
        User user = getByUsername(username);
        if (user.isEmailVerified() || user.needsEmailSetup()) {
            return; // 이미 인증됐거나 등록된 이메일이 없으면 보낼 것이 없음
        }
        sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = emailTokenService.consume(token, EmailToken.Purpose.VERIFY_EMAIL);
        user.setEmailVerified(true);
    }

    // 이메일 존재 여부와 무관하게 항상 같은 결과로 보이도록(계정 열거 방지), 실제 매칭 실패는
    // 여기서 조용히 무시하고 컨트롤러는 이 메서드 성공/실패와 상관없이 같은 안내 문구를 보여준다.
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() == User.Provider.GOOGLE) {
                mailService.sendGoogleAccountNotice(user);
                return;
            }
            String token = emailTokenService.issue(user, EmailToken.Purpose.RESET_PASSWORD);
            mailService.sendPasswordResetLink(user, token);
        });
    }

    @Transactional
    public void requestUsernameReminder(String email) {
        userRepository.findByEmail(email).ifPresent(mailService::sendUsernameReminder);
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmNewPassword) {
        if (newPassword == null || newPassword.isBlank() || !newPassword.equals(confirmNewPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        User user = emailTokenService.consume(token, EmailToken.Purpose.RESET_PASSWORD);
        user.setPassword(passwordEncoder.encode(newPassword));
        // 비로그인 상태에서 일어나는 조치라 actorUsername을 직접 넘긴다(register()와 동일한 이유).
        adminActionLogService.log("USER", user.getId(), "PASSWORD_RESET", null, user.getUsername());
    }

    // "내 프로필 설정" - 남이 보는 프로필(/users/{id})에 노출되는 소개글만 다루는 가벼운 수정.
    // 아이디/비밀번호/학교 정보(updateProfile())와는 목적이 달라서(계정 자체 관리 vs 남에게
    // 보이는 프로필 꾸미기) 별도 메서드로 분리했다 - 현재 비밀번호 재확인도 요구하지 않는다
    // (계정 보안과 무관한 낮은 위험도의 데이터라 확인 절차를 더할 필요가 없다고 판단).
    @Transactional
    public void updateBio(String username, String bio) {
        User user = getByUsername(username);
        if (bio != null && bio.length() > 150) {
            throw new IllegalArgumentException("소개글은 150자를 넘을 수 없습니다.");
        }
        user.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        adminActionLogService.log("USER", user.getId(), "BIO_UPDATE",
                bio == null || bio.isBlank() ? "(비움)" : truncate(bio.trim()));
    }

    // 알림 설정 - "콘테스트 마감 임박 알림"을 사용자가 직접 켜고 끄는 개인 설정(관리자 위임 권한과는
    // 성격이 다름, 계정 보안과 무관해 updateBio()와 동일하게 현재 비밀번호 재확인을 요구하지 않는다).
    // 지금은 항목이 하나뿐이지만 나중에 알림 유형이 늘어나도 이 메서드/화면을 그대로 확장하면 된다.
    @Transactional
    public void updateNotificationPreferences(String username, boolean contestDeadlineAlertEnabled) {
        User user = getByUsername(username);
        user.setContestDeadlineAlertEnabled(contestDeadlineAlertEnabled);
    }

    @Transactional
    public void updateProfileImage(String username, MultipartFile file) {
        User user = getByUsername(username);
        String url = fileUploadService.storeProfileImage(file, user.getProfileImageUrl());
        user.setProfileImageUrl(url);
        adminActionLogService.log("USER", user.getId(), "PROFILE_IMAGE_UPDATE", "프로필 사진 변경");
    }

    @Transactional
    public void resetProfileImage(String username) {
        User user = getByUsername(username);
        fileUploadService.deleteProfileImage(user.getProfileImageUrl());
        user.setProfileImageUrl(null);
        adminActionLogService.log("USER", user.getId(), "PROFILE_IMAGE_UPDATE", "기본 이미지로 되돌림");
    }

    // 계정 탈퇴(소프트 딜리트) - 본인 확인을 위해 현재 비밀번호를 재입력받는다
    @Transactional
    public void deleteAccount(String username, String password) {
        User user = getByUsername(username);

        // updateProfile()과 동일한 이유로 소셜 로그인 계정은 비밀번호 확인을 건너뛴다.
        if (user.getProvider() == User.Provider.LOCAL) {
            if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.getPassword())) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }
        }

        // 총관리자(admin, ROLE_SUPER_ADMIN)는 앱 안에서 다시 만들어낼 방법이 없는 유일한 계정이라
        // 마이페이지 자진 탈퇴 자체를 막는다. 부관리자(ROLE_ADMIN)는 총관리자가 항상 별도로 남아있으므로
        // "마지막 관리자 보호" 가드가 더 이상 필요 없다(예전엔 ROLE_ADMIN이 유일한 관리자 역할이라
        // 마지막 한 명이 탈퇴하면 관리자가 전멸했지만, 지금은 총관리자가 그 역할과 무관하게 항상 있다).
        if (user.isSuperAdmin()) {
            throw new IllegalArgumentException("총관리자 계정은 탈퇴할 수 없습니다.");
        }

        // 작성한 게시글/댓글은 그대로 남기고(작성자 FK 유지), 계정만 로그인 불가 상태로 전환한다.
        // 화면에는 실제 닉네임 대신 "탈퇴한 사용자"로 표시된다(PostService.displayNickname() 등 참고).
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        adminActionLogService.log("USER", user.getId(), "SELF_DELETE", username);
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}