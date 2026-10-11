package com.webschool.webschool.notice.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.notice.domain.Notice;
import com.webschool.webschool.notice.dto.NoticeDto;
import com.webschool.webschool.notice.repository.NoticeRepository;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 공지 작성/수정/삭제의 권한 규칙과 "활성 공지는 하나뿐" 규칙 회귀 테스트. 화면 접근은 AdminAccessInterceptor가
// 막지만, 서비스의 권한 검사가 빠지면 공지 권한이 없는 부관리자가 주소를 직접 호출해 전체 알림을 뿌릴 수 있다.
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock private NoticeRepository noticeRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AdminActionLogService adminActionLogService;

    @InjectMocks private NoticeService noticeService;

    private User noticeManager;

    @BeforeEach
    void setUp() {
        user("super", User.Role.ROLE_SUPER_ADMIN);
        noticeManager = user("noticeAdmin", User.Role.ROLE_ADMIN);
        noticeManager.setCanManageNotices(true);
        user("otherAdmin", User.Role.ROLE_ADMIN);
        user("student", User.Role.ROLE_USER);

        lenient().when(noticeRepository.save(any())).thenAnswer(invocation -> {
            Notice saved = invocation.getArgument(0);
            saved.setId(99L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
    }

    private User user(String username, User.Role role) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(username + "닉");
        user.setRole(role);
        lenient().when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private Notice notice(Long id, String title, String content, boolean active) {
        Notice n = new Notice();
        n.setId(id);
        n.setTitle(title);
        n.setContent(content);
        n.setAuthor(noticeManager);
        n.setActive(active);
        n.setCreatedAt(LocalDateTime.now());
        lenient().when(noticeRepository.findById(id)).thenReturn(Optional.of(n));
        return n;
    }

    // ---- 권한 ----

    @Test
    void create_withoutNoticePermission_isForbidden() {
        for (String username : List.of("otherAdmin", "student")) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> noticeService.createNotice(username, "제목", "내용"));
            assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        }
        verify(noticeRepository, never()).save(any());
        verify(notificationService, never()).broadcastAnnouncement(anyString(), anyString(), anyString());
    }

    @Test
    void updateAndDelete_withoutNoticePermission_areForbidden() {
        Notice n = notice(1L, "원래 제목", "원래 내용", true);

        assertThrows(BusinessException.class, () -> noticeService.updateNotice(1L, "otherAdmin", "바꿈", "바꿈"));
        assertThrows(BusinessException.class, () -> noticeService.deleteNotice(1L, "student"));

        assertEquals("원래 제목", n.getTitle());
        assertFalse(n.isDeleted());
    }

    @Test
    void create_bySuperAdminOrNoticeManager_isAllowed() {
        when(noticeRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Optional.empty());

        noticeService.createNotice("super", "제목", "내용");
        noticeService.createNotice("noticeAdmin", "제목", "내용");

        verify(noticeRepository, org.mockito.Mockito.times(2)).save(any());
    }

    // ---- 활성 공지는 하나뿐 ----

    @Test
    void create_archivesPreviousActiveNotice_andBroadcastsOnce() {
        Notice previous = notice(1L, "지난 공지", "내용", true);
        when(noticeRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Optional.of(previous));

        noticeService.createNotice("noticeAdmin", "  새 공지  ", "  새 내용  ");

        assertFalse(previous.isActive());
        org.mockito.ArgumentCaptor<Notice> captor = org.mockito.ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertTrue(captor.getValue().isActive());
        assertEquals("새 공지", captor.getValue().getTitle());
        assertEquals("새 내용", captor.getValue().getContent());
        // 작성자 본인은 알림 대상에서 빠지도록 username을 넘긴다.
        verify(notificationService).broadcastAnnouncement("[공지] 새 공지", "/notices", "noticeAdmin");
    }

    // 오타 수정에 전체 알림이 또 가면 안 된다.
    @Test
    void update_doesNotBroadcastAgain() {
        Notice n = notice(1L, "오타 재목", "내용", true);

        noticeService.updateNotice(1L, "noticeAdmin", "오타 제목", "내용");

        assertEquals("오타 제목", n.getTitle());
        assertTrue(n.getUpdatedAt() != null);
        verify(notificationService, never()).broadcastAnnouncement(anyString(), anyString(), anyString());
    }

    // ---- 입력 검증 ----

    @Test
    void create_rejectsBlankTooLongOrBannedInput() {
        assertThrows(IllegalArgumentException.class, () -> noticeService.createNotice("super", " ", "내용"));
        assertThrows(IllegalArgumentException.class, () -> noticeService.createNotice("super", "제목", null));
        assertThrows(IllegalArgumentException.class,
                () -> noticeService.createNotice("super", "가".repeat(101), "내용"));
        assertThrows(IllegalArgumentException.class,
                () -> noticeService.createNotice("super", "제목", "가".repeat(4001)));
        assertThrows(IllegalArgumentException.class, () -> noticeService.createNotice("super", "제목", "병신"));
        verify(noticeRepository, never()).save(any());
    }

    // 검증에 실패하면 이전 활성 공지를 내리면 안 된다(내리고 실패하면 활성 공지가 0개가 됨).
    @Test
    void create_withInvalidInput_keepsPreviousNoticeActive() {
        Notice previous = notice(1L, "지난 공지", "내용", true);
        lenient().when(noticeRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Optional.of(previous));

        assertThrows(IllegalArgumentException.class, () -> noticeService.createNotice("super", "", "내용"));

        assertTrue(previous.isActive());
    }

    // ---- 삭제된 공지 ----

    @Test
    void delete_isSoftDelete() {
        Notice n = notice(1L, "제목", "내용", true);

        noticeService.deleteNotice(1L, "noticeAdmin");

        assertTrue(n.isDeleted());
        assertTrue(n.getDeletedAt() != null);
        verify(noticeRepository, never()).delete(any());
    }

    @Test
    void deletedNotice_cannotBeReadOrUpdated() {
        Notice n = notice(1L, "제목", "내용", false);
        n.setDeleted(true);

        assertThrows(BusinessException.class, () -> noticeService.getDetail(1L));
        assertThrows(BusinessException.class, () -> noticeService.updateNotice(1L, "super", "새 제목", "새 내용"));
        assertEquals("제목", n.getTitle());
    }

    @Test
    void unknownNotice_isNotFound() {
        when(noticeRepository.findById(404L)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> noticeService.getDetail(404L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    // ---- 조회 ----

    @Test
    void history_filtersByKeywordInTitleOrContent_caseInsensitive() {
        List<Notice> notices = List.of(
                notice(1L, "Exam 일정", "중간고사", true),
                notice(2L, "급식 안내", "exam 기간 급식", false),
                notice(3L, "방학 안내", "겨울방학", false));
        when(noticeRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(notices);

        Page<NoticeDto> all = noticeService.getHistory(0, 10, null);
        Page<NoticeDto> filtered = noticeService.getHistory(0, 10, "  EXAM ");

        assertEquals(3, all.getTotalElements());
        assertEquals(List.of(1L, 2L), filtered.getContent().stream().map(NoticeDto::getId).toList());
    }

    // 작성한 관리자가 하드 삭제돼도(author == null) 공지 화면이 깨지면 안 된다.
    @Test
    void noticeOfDeletedAuthor_showsPlaceholderNickname() {
        Notice n = notice(1L, "제목", "내용", true);
        n.setAuthor(null);

        assertEquals("탈퇴한 관리자", noticeService.getDetail(1L).getAuthorNickname());
    }
}
