package com.webschool.webschool.user.service;

import com.webschool.webschool.user.dto.MyPageUpdateDto;
import com.webschool.webschool.user.dto.RegisterDto;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword())); // BCrypt 암호화
        user.setNickname(nickname);
        user.setSchoolName(dto.getSchoolName());
        user.setSchoolCode(dto.getSchoolCode());
        user.setAtptCode(dto.getAtptCode());
        user.setSchoolKind(dto.getSchoolKind());
        user.setGrade(dto.getGrade());
        user.setClassNum(dto.getClassNum());
        user.setRole(User.Role.ROLE_USER);

        userRepository.save(user);
    }

    /**
     * 마이페이지 정보 수정. 아이디가 변경되면 true를 반환 (세션 재로그인 필요).
     */
    @Transactional
    public boolean updateProfile(String currentUsername, MyPageUpdateDto dto) {
        User user = getByUsername(currentUsername);

        if (dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()
                || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
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
            user.setUsername(newUsername);
            usernameChanged = true;
        }

        if (dto.getNewPassword() != null && !dto.getNewPassword().isBlank()) {
            if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
                throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다.");
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        String nickname = dto.getNickname();
        user.setNickname((nickname == null || nickname.isBlank()) ? user.getUsername() : nickname.trim());

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
        User user = getByUsername(username);
        if (bio != null && bio.length() > 150) {
            throw new IllegalArgumentException("소개글은 150자를 넘을 수 없습니다.");
        }
        user.setBio(bio == null || bio.isBlank() ? null : bio.trim());
    }

    // 계정 탈퇴(소프트 딜리트) - 본인 확인을 위해 현재 비밀번호를 재입력받는다
    @Transactional
    public void deleteAccount(String username, String password) {
        User user = getByUsername(username);

        if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
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
    }
}