package com.webschool.webschool.user.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.user.dto.AdminUserProfileDto;
import com.webschool.webschool.user.dto.AdminUserProfilePostDto;
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

// 관리자(ROLE_ADMIN/ROLE_SUPER_ADMIN) 전용 계정 관리 로직. 기존 UserService(회원가입/마이페이지 플로우)는
// 건드리지 않고 분리했다. 이 서비스가 다루는 액션(권한 승격/해제, 권한 토글, 비활성화 등)은 전부
// AdminAccessInterceptor가 이미 "/admin/users/**"를 총관리자(ROLE_SUPER_ADMIN) 전용으로 막아두지만,
// 컨트롤러 하나만 믿지 않고 서비스 단에서도 한 번 더 방어적으로 검증한다(기존 코드 스타일과 동일).
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final NotificationService notificationService;

    // keyword: 아이디/닉네임/학교명 검색 - 다른 관리자 목록(AdminPostService 등)과 동일하게 DB 쿼리가
    // 아니라 메모리에서 필터링한다(계정 수가 적을 걸 가정). **버그 수정**: user-list.html엔 검색창이
    // 이미 있었는데 컨트롤러가 keyword 파라미터 자체를 안 받아서 항상 전체 목록만 보였다.
    public List<AdminUserSummaryDto> getAllUsers(String keyword) {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getUsername(), dto.getNickname(), dto.getSchoolName()))
                .collect(Collectors.toList());
    }

    private boolean matches(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase();
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    // 총관리자 전용 "관리자 권한 관리" 페이지 - 관리자 계정(총관리자 포함)만 추려서 보여준다
    public List<AdminUserSummaryDto> getAllAdmins() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .filter(User::isAdmin)
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    public AdminUserProfileDto getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<AdminUserProfilePostDto> recentPosts = postRepository
                .findTop5ByAuthor_IdAndDeletedFalseOrderByCreatedAtDesc(id).stream()
                .map(this::toProfilePostDto)
                .collect(Collectors.toList());

        return AdminUserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .schoolName(user.getSchoolName())
                .schoolKind(user.getSchoolKind())
                .grade(user.getGrade())
                .classNum(user.getClassNum())
                .deleted(user.isDeleted())
                .deletedAt(user.getDeletedAt() != null ? user.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .active(user.isActive())
                .canManageReports(user.isCanManageReports())
                .canManagePosts(user.isCanManagePosts())
                .canManageScheduleComments(user.isCanManageScheduleComments())
                .postCount(postRepository.countByAuthor_IdAndDeletedFalse(id))
                .commentCount(postCommentRepository.countByAuthor_IdAndDeletedFalse(id))
                .recentPosts(recentPosts)
                .build();
    }

    @Transactional
    public void setRole(Long id, User.Role role, String actingAdminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getUsername().equals(actingAdminUsername)) {
            throw new IllegalArgumentException("본인의 권한은 관리자 페이지에서 변경할 수 없습니다.");
        }

        // 총관리자 계정은 이 화면에서 권한을 뺏거나 다른 계정을 총관리자로 만들 수 없다 - admin 계정 하나로 고정
        if (user.isSuperAdmin()) {
            throw new IllegalArgumentException("총관리자 권한은 변경할 수 없습니다.");
        }

        // 탈퇴한 계정은 로그인 자체가 안 되므로 권한을 바꿔도 의미가 없다 - 혼란 방지를 위해 아예 막는다
        if (user.isDeleted()) {
            throw new IllegalArgumentException("탈퇴한 계정은 권한을 변경할 수 없습니다.");
        }

        // (구) "마지막 남은 관리자는 강등 불가" 가드는 ROLE_ADMIN이 유일한 관리자 역할이던 시절의
        // 방어 로직이었다. 지금은 총관리자(admin, ROLE_SUPER_ADMIN)가 부관리자 수와 무관하게 항상
        // 별도로 존재하므로, 마지막 부관리자를 강등해도 시스템에 관리자가 하나도 안 남는 일은 생기지
        // 않는다 - 이 가드를 유지하면 총관리자가 유일한 부관리자를 해제하지 못하는 버그가 된다.

        user.setRole(role);

        // 부관리자에서 학생으로 강등되면 예전에 켜둔 권한이 그대로 남아있다가, 나중에 아무 확인 없이
        // 다시 승격됐을 때 부활하면 안 되므로 강등 시점에 전부 꺼둔다
        if (role == User.Role.ROLE_USER) {
            user.setCanManageReports(false);
            user.setCanManagePosts(false);
            user.setCanManageScheduleComments(false);
            notificationService.notify(user, Notification.Type.ACCOUNT, "관리자 권한이 해제되었습니다.", "/mypage");
        } else if (role == User.Role.ROLE_ADMIN) {
            notificationService.notify(user, Notification.Type.ACCOUNT,
                    "부관리자로 승격되었습니다. 총관리자가 켜준 권한만 사용할 수 있어요.", "/mypage");
        }
    }

    // 부관리자 권한(신고/게시글/한마디 관리) 토글 - 총관리자만 호출 가능(컨트롤러/인터셉터에서 이미 보장)
    @Transactional
    public void updatePermissions(Long id, boolean canManageReports, boolean canManagePosts,
                                   boolean canManageScheduleComments) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getRole() != User.Role.ROLE_ADMIN) {
            throw new IllegalArgumentException("부관리자 계정에만 권한을 지정할 수 있습니다.");
        }

        user.setCanManageReports(canManageReports);
        user.setCanManagePosts(canManagePosts);
        user.setCanManageScheduleComments(canManageScheduleComments);
    }

    // 관리자 강제 탈퇴 처리 - 본인 확인(비밀번호) 없이 소프트 삭제한다는 점만 UserService.deleteAccount()와 다름
    @Transactional
    public void deleteUser(Long id, String actingAdminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getUsername().equals(actingAdminUsername)) {
            throw new IllegalArgumentException("본인 계정은 관리자 페이지에서 삭제할 수 없습니다. 마이페이지를 이용하세요.");
        }

        if (user.isSuperAdmin()) {
            throw new IllegalArgumentException("총관리자 계정은 탈퇴 처리할 수 없습니다.");
        }

        // setRole()과 동일한 이유로 "마지막 관리자 보호" 가드는 두지 않는다 - 총관리자가 항상 별도로
        // 존재하므로 마지막 부관리자를 탈퇴 처리해도 시스템에 관리자가 하나도 안 남는 일은 없다.

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

    // 계정 비활성화(정지) - 탈퇴와 달리 계정/작성 글은 전부 그대로 둔 채 로그인만 막는다. 총관리자가
    // 문제 있는 계정을 즉시 정지시키고, 조사가 끝나면 다시 활성화할 수 있는 가벼운 조치다.
    @Transactional
    public void deactivateUser(Long id, String actingAdminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getUsername().equals(actingAdminUsername)) {
            throw new IllegalArgumentException("본인 계정은 비활성화할 수 없습니다.");
        }

        if (user.isSuperAdmin()) {
            throw new IllegalArgumentException("총관리자 계정은 비활성화할 수 없습니다.");
        }

        if (user.isDeleted()) {
            throw new IllegalArgumentException("탈퇴한 계정입니다. 별도로 비활성화할 필요가 없습니다.");
        }

        user.setActive(false);
        // 링크를 안 넣는 이유: 비활성화된 동안은 로그인 자체가 막혀서 어차피 못 열어본다 - 다시
        // 활성화된 뒤 로그인하면 알림 목록에서 확인할 수 있다.
        notificationService.notify(user, Notification.Type.ACCOUNT, "계정이 비활성화(정지)되었습니다.", null);
    }

    @Transactional
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setActive(true);
        notificationService.notify(user, Notification.Type.ACCOUNT, "계정이 다시 활성화되었습니다. 로그인할 수 있어요.", "/mypage");
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
                .active(user.isActive())
                .canManageReports(user.isCanManageReports())
                .canManagePosts(user.isCanManagePosts())
                .canManageScheduleComments(user.isCanManageScheduleComments())
                .build();
    }

    private AdminUserProfilePostDto toProfilePostDto(Post post) {
        return AdminUserProfilePostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .categoryLabel(post.getCategory().getLabel())
                .createdAt(post.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
