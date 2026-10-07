package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserPenalty;
import com.webschool.webschool.user.point.service.UserPointService;
import com.webschool.webschool.user.repository.UserPenaltyRepository;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 제재(경고/작성정지/임시차단/비활성화) 회귀 테스트. 제재 상태는 User에 저장하지 않고 이력에서 매번 계산하므로,
// "어떤 제재가 무엇을 막는가"의 연결이 끊기면 제재를 걸어도 아무 일도 안 일어난다(에러 없이).
@ExtendWith(MockitoExtension.class)
class UserPenaltyServiceTest {

    @Mock private UserPenaltyRepository userPenaltyRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AdminActionLogService adminActionLogService;
    @Mock private UserPointService userPointService;

    @InjectMocks private UserPenaltyService userPenaltyService;

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

    private void activePenalty(UserPenalty.Type type, LocalDateTime expiresAt) {
        UserPenalty penalty = new UserPenalty();
        penalty.setType(type);
        penalty.setReason("도배");
        penalty.setExpiresAt(expiresAt);
        lenient().when(userPenaltyRepository.findActive(eq(3L), eq(type), any())).thenReturn(List.of(penalty));
    }

    private UserPenalty savedPenalty() {
        ArgumentCaptor<UserPenalty> captor = ArgumentCaptor.forClass(UserPenalty.class);
        verify(userPenaltyRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---- 부여 ----

    @Test
    void issue_savesPenaltyAndDeductsPointsForThatType() {
        userPenaltyService.issue(3L, UserPenalty.Type.POST_SUSPENSION, " 도배 ", 7, "subadmin");

        UserPenalty penalty = savedPenalty();
        assertEquals(student, penalty.getTarget());
        assertEquals(subAdmin, penalty.getIssuedBy());
        assertEquals("도배", penalty.getReason());
        assertNotNull(penalty.getExpiresAt());
        verify(userPointService).deductForPenalty(eq(student), eq(10), anyString());
    }

    @Test
    void issue_withoutDuration_isPermanent() {
        userPenaltyService.issue(3L, UserPenalty.Type.WARNING, "경고", null, "subadmin");

        assertNull(savedPenalty().getExpiresAt());
    }

    @Test
    void issue_cannotTargetSelfOrSuperAdmin() {
        assertThrows(IllegalArgumentException.class,
                () -> userPenaltyService.issue(2L, UserPenalty.Type.WARNING, "사유", 1, "subadmin"));
        // 부관리자가 총관리자를 비활성화 제재로 잠가버리는 경로.
        assertThrows(IllegalArgumentException.class,
                () -> userPenaltyService.issue(1L, UserPenalty.Type.DEACTIVATION, "사유", null, "subadmin"));

        verify(userPenaltyRepository, never()).save(any());
        verify(userPointService, never()).deductForPenalty(any(), anyInt(), anyString());
    }

    @Test
    void issue_requiresReason() {
        assertThrows(IllegalArgumentException.class,
                () -> userPenaltyService.issue(3L, UserPenalty.Type.WARNING, "   ", 1, "subadmin"));
        verify(userPenaltyRepository, never()).save(any());
    }

    // 사유 컬럼 길이(300)를 넘기면 DB 예외로 500이 나는 대신 잘라서 저장한다.
    @Test
    void issue_truncatesOverlongReason() {
        userPenaltyService.issue(3L, UserPenalty.Type.WARNING, "가".repeat(400), 1, "subadmin");

        assertEquals(300, savedPenalty().getReason().length());
    }

    // ---- 해제 ----

    @Test
    void revoke_marksRevokedOnce() {
        UserPenalty penalty = new UserPenalty();
        penalty.setTarget(student);
        penalty.setType(UserPenalty.Type.WARNING);
        lenient().when(userPenaltyRepository.findById(50L)).thenReturn(Optional.of(penalty));

        userPenaltyService.revoke(50L);
        assertTrue(penalty.isRevoked());
        assertFalse(penalty.isCurrentlyActive());

        assertThrows(BusinessException.class, () -> userPenaltyService.revoke(50L));
    }

    // 대상 계정이 하드 삭제돼 참조가 끊긴 제재 - NPE(500) 대신 안내 메시지.
    @Test
    void revoke_whenTargetHardDeleted_failsGracefully() {
        UserPenalty penalty = new UserPenalty();
        penalty.setType(UserPenalty.Type.WARNING);
        lenient().when(userPenaltyRepository.findById(51L)).thenReturn(Optional.of(penalty));

        assertThrows(BusinessException.class, () -> userPenaltyService.revoke(51L));
        assertFalse(penalty.isRevoked());
    }

    // ---- 제재가 실제로 무엇을 막는가 ----

    @Test
    void noPenalty_allowsEverything() {
        assertDoesNotThrow(() -> userPenaltyService.assertCanCreatePost(student));
        assertDoesNotThrow(() -> userPenaltyService.assertCanComment(student));
        assertFalse(userPenaltyService.isDeactivated(3L));
    }

    // 게시글 작성정지는 글쓰기만 막는다 - 댓글은 계속 쓸 수 있다.
    @Test
    void postSuspension_blocksPostsButNotComments() {
        activePenalty(UserPenalty.Type.POST_SUSPENSION, LocalDateTime.now().plusDays(3));

        assertThrows(IllegalArgumentException.class, () -> userPenaltyService.assertCanCreatePost(student));
        assertDoesNotThrow(() -> userPenaltyService.assertCanComment(student));
    }

    // 커뮤니티 임시차단은 더 넓은 제재라 글쓰기와 댓글을 모두 막는다.
    @Test
    void communitySuspension_blocksPostsAndComments() {
        activePenalty(UserPenalty.Type.COMMUNITY_SUSPENSION, null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userPenaltyService.assertCanComment(student));
        assertTrue(e.getMessage().contains("무기한"));
        assertTrue(e.getMessage().contains("도배"));
        assertThrows(IllegalArgumentException.class, () -> userPenaltyService.assertCanCreatePost(student));
    }

    // 경고는 기록과 포인트 차감만 있고 아무것도 막지 않는다. 비활성화 제재만 로그인을 막는다.
    @Test
    void onlyDeactivationPenalty_blocksLogin() {
        activePenalty(UserPenalty.Type.WARNING, null);
        assertFalse(userPenaltyService.isDeactivated(3L));
        assertDoesNotThrow(() -> userPenaltyService.assertCanCreatePost(student));

        activePenalty(UserPenalty.Type.DEACTIVATION, LocalDateTime.now().plusDays(1));
        assertTrue(userPenaltyService.isDeactivated(3L));
    }
}
