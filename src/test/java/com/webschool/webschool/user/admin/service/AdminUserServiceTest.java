package com.webschool.webschool.user.admin.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.comment.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.point.service.UserPointService;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPenaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 관리자 계정 관리의 권한 상승 방지 규칙 회귀 테스트. 화면 접근은 AdminAccessInterceptor가 막지만 이 서비스가
// "컨트롤러 하나만 믿지 않고 한 번 더" 검증하는 마지막 방어선이라(클래스 주석), 여기 규칙이 조용히 빠지면
// 부관리자가 자기 권한을 스스로 올리거나 총관리자를 끌어내릴 수 있다 - 컴파일 에러 없이 깨지는 유형.
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserPenaltyService userPenaltyService;
    @Mock private AdminActionLogService adminActionLogService;
    @Mock private UserPointService userPointService;

    @InjectMocks private AdminUserService adminUserService;

    private User superAdmin;
    private User subAdmin;
    private User student;

    @BeforeEach
    void setUp() {
        superAdmin = user(1L, "admin", User.Role.ROLE_SUPER_ADMIN);
        subAdmin = user(2L, "subadmin", User.Role.ROLE_ADMIN);
        student = user(3L, "test1", User.Role.ROLE_USER);
    }

    private User user(Long id, String username, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        lenient().when(userRepository.findById(id)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private void updatePermissions(Long targetId, String actor, boolean value) {
        adminUserService.updatePermissions(targetId, actor, value, value, value, value, value, value, value,
                value, value, value, value, value, value, value, value);
    }

    // ---- 자기 자신에게는 아무것도 못 한다 ----

    @Test
    void cannotChangeOwnRole() {
        assertThrows(BusinessException.class,
                () -> adminUserService.setRole(2L, User.Role.ROLE_USER, "subadmin"));
        assertEquals(User.Role.ROLE_ADMIN, subAdmin.getRole());
    }

    // canManageAdminPermissions를 받은 부관리자가 자기 자신에게 나머지 권한까지 켜는 경로.
    @Test
    void cannotGrantPermissionsToSelf() {
        assertThrows(BusinessException.class, () -> updatePermissions(2L, "subadmin", true));
        assertFalse(subAdmin.isCanManageUsers());
        assertFalse(subAdmin.isCanManagePoints());
    }

    // canManagePoints가 있는 관리자가 자기에게 포인트를 무제한 지급하는 경로.
    @Test
    void cannotAdjustOwnPoints() {
        assertThrows(BusinessException.class,
                () -> adminUserService.adjustPoints(2L, 1000, "이벤트", "subadmin"));
        verify(userPointService, never()).adjustByAdmin(any(), anyInt(), anyString());
    }

    @Test
    void cannotDeleteOrDeactivateSelf() {
        assertThrows(BusinessException.class, () -> adminUserService.deleteUser(2L, "subadmin"));
        assertThrows(BusinessException.class, () -> adminUserService.deactivateUser(2L, "subadmin"));
        assertFalse(subAdmin.isDeleted());
        assertTrue(subAdmin.isActive());
    }

    // ---- 총관리자는 이 화면에서 건드릴 수 없다 ----

    @Test
    void superAdminCannotBeDemotedDeletedOrDeactivated() {
        assertThrows(IllegalArgumentException.class,
                () -> adminUserService.setRole(1L, User.Role.ROLE_USER, "subadmin"));
        assertThrows(IllegalArgumentException.class, () -> adminUserService.deleteUser(1L, "subadmin"));
        assertThrows(IllegalArgumentException.class, () -> adminUserService.deactivateUser(1L, "subadmin"));

        assertEquals(User.Role.ROLE_SUPER_ADMIN, superAdmin.getRole());
        assertFalse(superAdmin.isDeleted());
        assertTrue(superAdmin.isActive());
    }

    // ---- 총관리자 승격은 총관리자만, 부관리자에게만 ----

    // 권한 부여 화면에 들어올 수 있는(canManageAdminPermissions) 부관리자라도 총관리자를 늘릴 수는 없어야 한다.
    @Test
    void subAdminCannotPromoteAnyoneToSuperAdmin() {
        User another = user(4L, "subadmin2", User.Role.ROLE_ADMIN);
        subAdmin.setCanManageAdminPermissions(true);

        assertThrows(IllegalArgumentException.class, () -> adminUserService.promoteToSuperAdmin(4L, "subadmin"));
        assertEquals(User.Role.ROLE_ADMIN, another.getRole());
    }

    @Test
    void superAdminCanPromoteSubAdminButNotStudent() {
        adminUserService.promoteToSuperAdmin(2L, "admin");
        assertEquals(User.Role.ROLE_SUPER_ADMIN, subAdmin.getRole());

        assertThrows(IllegalArgumentException.class, () -> adminUserService.promoteToSuperAdmin(3L, "admin"));
        assertEquals(User.Role.ROLE_USER, student.getRole());
    }

    @Test
    void deletedSubAdminCannotBePromotedToSuperAdmin() {
        subAdmin.setDeleted(true);

        assertThrows(IllegalArgumentException.class, () -> adminUserService.promoteToSuperAdmin(2L, "admin"));
        assertEquals(User.Role.ROLE_ADMIN, subAdmin.getRole());
    }

    // ---- 세부 권한 ----

    // 학생 계정에 권한 플래그가 켜지면, 나중에 부관리자로 승격되는 순간 아무도 확인하지 않은 권한이 살아난다.
    @Test
    void permissionsCanOnlyBeSetOnSubAdmins() {
        assertThrows(IllegalArgumentException.class, () -> updatePermissions(3L, "admin", true));
        assertFalse(student.hasAnyAdminAccess());
    }

    // 강등 때 권한을 "전부" 꺼야 재승격 시 예전 권한이 되살아나지 않는다. 처음엔 예전 9개만 끄고 나중에
    // 추가된 6개(출석/포인트/대시보드/부하테스트/문의/에러로그)가 남아 있었다 - 이 테스트로 발견.
    @Test
    void demotionClearsEveryPermissionFlag() {
        updatePermissions(2L, "admin", true);
        assertTrue(subAdmin.hasAnyAdminAccess());

        adminUserService.setRole(2L, User.Role.ROLE_USER, "admin");

        assertEquals(User.Role.ROLE_USER, subAdmin.getRole());
        assertFalse(subAdmin.hasAnyAdminAccess());
    }

    // ---- 그 밖의 입력 검증 ----

    @Test
    void deletedAccountRoleCannotBeChanged() {
        student.setDeleted(true);

        assertThrows(IllegalArgumentException.class,
                () -> adminUserService.setRole(3L, User.Role.ROLE_ADMIN, "admin"));
        assertEquals(User.Role.ROLE_USER, student.getRole());
    }

    @Test
    void pointAdjustmentNeedsNonZeroAmountAndReason() {
        assertThrows(IllegalArgumentException.class, () -> adminUserService.adjustPoints(3L, 0, "사유", "admin"));
        assertThrows(IllegalArgumentException.class, () -> adminUserService.adjustPoints(3L, 10, "  ", "admin"));
        verify(userPointService, never()).adjustByAdmin(any(), anyInt(), anyString());

        adminUserService.adjustPoints(3L, -10, " 오적립 정정 ", "admin");
        verify(userPointService).adjustByAdmin(student, -10, "오적립 정정");
    }

    // 관리자 강제 탈퇴는 deletedByAdmin 표식이 있어야 구글 재로그인 자동 복구 대상에서 빠진다.
    @Test
    void adminDeletionIsMarkedAndRestoreClearsIt() {
        adminUserService.deleteUser(3L, "admin");
        assertTrue(student.isDeleted());
        assertTrue(student.isDeletedByAdmin());

        adminUserService.restoreUser(3L);
        assertFalse(student.isDeleted());
        assertFalse(student.isDeletedByAdmin());
    }
}
