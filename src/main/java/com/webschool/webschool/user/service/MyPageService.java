package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.global.upload.FileUploadService;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.dto.MyPageUpdateDto;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

// 로그인 후 마이페이지의 계정 설정 - 내 정보 수정, 남이 보는 프로필(소개글/사진), 알림 설정,
// 탈퇴. 2026-09-28 UserService에서 분리.
@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminActionLogService adminActionLogService;
    private final FileUploadService fileUploadService;

    /**
     * 마이페이지 정보 수정. 아이디가 변경되면 true를 반환 (세션 재로그인 필요).
     */
    @Transactional
    public boolean updateProfile(String currentUsername, MyPageUpdateDto dto) {
        User user = userService.getByUsername(currentUsername);

        // 소셜 로그인(GOOGLE) 계정은 본인도 모르는 임의 비밀번호가 들어있어(User.password 필드 주석
        // 참고) 현재 비밀번호 확인 자체가 성립하지 않는다 - LOCAL 계정에만 이 확인을 요구한다.
        if (user.getProvider() == User.Provider.LOCAL) {
            if (dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()
                    || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }
        }

        UserInputValidator.requireSchoolSelected(dto.getSchoolName(), dto.getSchoolCode(),
                dto.getGrade(), dto.getClassNum());

        boolean usernameChanged = false;
        String newUsername = dto.getUsername() == null ? "" : dto.getUsername().trim();

        if (!newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            UserInputValidator.requireValidUsername(newUsername);
            if (userRepository.existsByUsername(newUsername)) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
            }
            String oldUsername = user.getUsername();
            user.setUsername(newUsername);
            usernameChanged = true;
            adminActionLogService.log("USER", user.getId(), "USERNAME_CHANGE", oldUsername + " -> " + newUsername);
        }

        // 비밀번호 변경은 LOCAL 계정 전용(mypage-edit.html도 LOCAL에만 이 섹션을 보여준다) - 구글
        // 계정은 폼 로그인을 쓰지 않으므로 비밀번호 개념 자체가 없다(User.password 필드 주석 참고).
        if (user.getProvider() == User.Provider.LOCAL
                && dto.getNewPassword() != null && !dto.getNewPassword().isBlank()) {
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

        String newEmail = UserInputValidator.requireValidEmail(dto.getEmail());
        if (!newEmail.equals(user.getEmail())) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
            }
            user.setEmail(newEmail);
            user.setEmailVerified(false); // 이메일이 바뀌었으니 새 주소로 다시 인증해야 함
            userService.sendVerification(user);
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

    // "내 프로필 설정" - 남이 보는 프로필(/users/{id})에 노출되는 소개글만 다루는 가벼운 수정.
    // 아이디/비밀번호/학교 정보(updateProfile())와는 목적이 달라서(계정 자체 관리 vs 남에게
    // 보이는 프로필 꾸미기) 별도 메서드로 분리했다 - 현재 비밀번호 재확인도 요구하지 않는다
    // (계정 보안과 무관한 낮은 위험도의 데이터라 확인 절차를 더할 필요가 없다고 판단).
    @Transactional
    public void updateBio(String username, String bio) {
        User user = userService.getByUsername(username);
        if (bio != null && bio.length() > 150) {
            throw new IllegalArgumentException("소개글은 150자를 넘을 수 없습니다.");
        }
        user.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        adminActionLogService.log("USER", user.getId(), "BIO_UPDATE",
                bio == null || bio.isBlank() ? "(비움)" : truncate(bio.trim()));
    }

    // 알림 설정 - 사용자가 직접 켜고 끄는 개인 알림 설정(관리자 위임 권한과는 성격이 다름, 계정
    // 보안과 무관해 updateBio()와 동일하게 현재 비밀번호 재확인을 요구하지 않는다). 댓글/좋아요/
    // 답글은 기존에 항상 켜져 있던 알림을 끄는 옵트아웃(NotificationService.isEnabled() 참고) -
    // 체크박스 미체크 시 폼에서 아예 파라미터가 안 넘어오므로 컨트롤러 쪽에서 각 필드를
    // defaultValue=false로 받아 그대로 전달한다.
    @Transactional
    public void updateNotificationPreferences(String username, boolean commentAlertEnabled,
                                               boolean likeAlertEnabled, boolean replyAlertEnabled) {
        User user = userService.getByUsername(username);
        user.setCommentAlertEnabled(commentAlertEnabled);
        user.setLikeAlertEnabled(likeAlertEnabled);
        user.setReplyAlertEnabled(replyAlertEnabled);
    }

    @Transactional
    public void updateProfileImage(String username, MultipartFile file) {
        User user = userService.getByUsername(username);
        String url = fileUploadService.storeProfileImage(file, user.getProfileImageUrl());
        user.setProfileImageUrl(url);
        adminActionLogService.log("USER", user.getId(), "PROFILE_IMAGE_UPDATE", "프로필 사진 변경");
    }

    @Transactional
    public void resetProfileImage(String username) {
        User user = userService.getByUsername(username);
        fileUploadService.deleteProfileImage(user.getProfileImageUrl());
        user.setProfileImageUrl(null);
        adminActionLogService.log("USER", user.getId(), "PROFILE_IMAGE_UPDATE", "기본 이미지로 되돌림");
    }

    // 계정 탈퇴(소프트 딜리트) - 본인 확인을 위해 현재 비밀번호를 재입력받는다
    @Transactional
    public void deleteAccount(String username, String password) {
        User user = userService.getByUsername(username);

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
