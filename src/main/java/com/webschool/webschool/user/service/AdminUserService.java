package com.webschool.webschool.user.service;

import com.webschool.webschool.user.dto.AdminUserSummaryDto;
import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 관리자(ROLE_ADMIN) 전용 계정 관리 로직. 기존 UserService(회원가입/마이페이지 플로우)는 건드리지 않고 분리했다.
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final UserRepository userRepository;

    public List<AdminUserSummaryDto> getAllUsers() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void setRole(Long id, User.Role role, String actingAdminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getUsername().equals(actingAdminUsername)) {
            throw new IllegalArgumentException("본인의 권한은 관리자 페이지에서 변경할 수 없습니다.");
        }

        // 탈퇴한 계정은 로그인 자체가 안 되므로 권한을 바꿔도 의미가 없다 - 혼란 방지를 위해 아예 막는다
        if (user.isDeleted()) {
            throw new IllegalArgumentException("탈퇴한 계정은 권한을 변경할 수 없습니다.");
        }

        // 마지막 남은 관리자를 강등시키면 시스템에 관리자가 한 명도 안 남을 수 있다 - 방지
        if (user.getRole() == User.Role.ROLE_ADMIN && role == User.Role.ROLE_USER
                && userRepository.countByRoleAndDeletedFalse(User.Role.ROLE_ADMIN) <= 1) {
            throw new IllegalArgumentException("마지막 남은 관리자 계정의 권한은 해제할 수 없습니다. 다른 계정을 먼저 관리자로 지정해주세요.");
        }

        user.setRole(role);
    }

    // 관리자 강제 탈퇴 처리 - 본인 확인(비밀번호) 없이 소프트 삭제한다는 점만 UserService.deleteAccount()와 다름
    @Transactional
    public void deleteUser(Long id, String actingAdminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getUsername().equals(actingAdminUsername)) {
            throw new IllegalArgumentException("본인 계정은 관리자 페이지에서 삭제할 수 없습니다. 마이페이지를 이용하세요.");
        }

        // 마지막 남은 관리자를 탈퇴 처리하면 시스템에 관리자가 한 명도 안 남을 수 있다 - 방지
        if (user.getRole() == User.Role.ROLE_ADMIN
                && userRepository.countByRoleAndDeletedFalse(User.Role.ROLE_ADMIN) <= 1) {
            throw new IllegalArgumentException("마지막 남은 관리자 계정은 탈퇴 처리할 수 없습니다. 다른 계정을 먼저 관리자로 지정해주세요.");
        }

        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public void restoreUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setDeleted(false);
        user.setDeletedAt(null);
    }

    private AdminUserSummaryDto toSummaryDto(User user) {
        return AdminUserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .schoolName(user.getSchoolName())
                .deleted(user.isDeleted())
                .deletedAt(user.getDeletedAt() != null ? user.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .build();
    }
}
