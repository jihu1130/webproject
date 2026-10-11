package com.webschool.webschool.school.comment.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.school.comment.domain.ScheduleComment;
import com.webschool.webschool.school.comment.dto.ScheduleCommentDto;
import com.webschool.webschool.school.comment.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.comment.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.comment.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.comment.repository.ScheduleCommentRepository;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.repository.SchoolRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.profile.service.UserBlockService;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPenaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 오늘의 한마디 작성/수정/삭제의 소유권 규칙, 블라인드·차단 노출 규칙, XSS 정제 회귀 테스트.
// 한마디 본문은 th:utext로 그대로 그려지므로 저장 전 정제가 유일한 XSS 방어선이다.
@ExtendWith(MockitoExtension.class)
class ScheduleCommentServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 12);
    private static final String BLIND_PLACEHOLDER = "신고 누적으로 블라인드 처리된 한마디입니다.";

    @Mock private ScheduleCommentRepository scheduleCommentRepository;
    @Mock private ScheduleCommentReportRepository scheduleCommentReportRepository;
    @Mock private ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    @Mock private ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPenaltyService userPenaltyService;
    @Mock private UserBlockService userBlockService;
    @Mock private AdminActionLogService adminActionLogService;

    @InjectMocks private ScheduleCommentService scheduleCommentService;

    private User writer;
    private User stranger;
    private School school;
    private ScheduleComment comment;

    @BeforeEach
    void setUp() {
        writer = user(1L, "writer", User.Role.ROLE_USER);
        stranger = user(2L, "stranger", User.Role.ROLE_USER);
        user(3L, "admin", User.Role.ROLE_ADMIN);
        user(4L, "super", User.Role.ROLE_SUPER_ADMIN);

        school = School.builder().id(7L).atptOfcdcScCode("N10").sdSchulCode("8181104").schoolName("테스트중").build();
        lenient().when(schoolRepository.findBySdSchulCode("8181104")).thenReturn(Optional.of(school));

        comment = comment(100L, writer, "오늘 급식 맛있다");

        lenient().when(scheduleCommentRepository.save(any())).thenAnswer(invocation -> {
            ScheduleComment saved = invocation.getArgument(0);
            saved.setId(500L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
    }

    private User user(Long id, String username, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username + "닉");
        user.setRole(role);
        lenient().when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private ScheduleComment comment(Long id, User author, String content) {
        ScheduleComment c = new ScheduleComment();
        c.setId(id);
        c.setSchool(school);
        c.setTargetDate(DATE);
        c.setGrade("3");
        c.setClassNm("1");
        c.setUser(author);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        lenient().when(scheduleCommentRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private ScheduleCommentDto create(String username, String content, boolean pollAttached) {
        return scheduleCommentService.createComment("N10", "8181104", DATE, "3", "1", username, content, pollAttached);
    }

    private List<ScheduleCommentDto> list(String viewer, ScheduleComment... comments) {
        when(scheduleCommentRepository
                .findBySchool_IdAndTargetDateAndGradeAndClassNmAndDeletedFalseOrderByCreatedAtAsc(7L, DATE, "3", "1"))
                .thenReturn(List.of(comments));
        return scheduleCommentService.getComments("N10", "8181104", DATE, "3", "1", viewer);
    }

    // ---- 수정·삭제는 본인만 ----

    @Test
    void update_byAnotherUser_isForbidden() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> scheduleCommentService.updateComment(100L, "stranger", "내가 바꿈"));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        assertEquals("오늘 급식 맛있다", comment.getContent());
    }

    // 관리자도 이 경로로는 남의 한마디를 못 지운다(관리자 삭제는 관리자 화면의 별도 서비스).
    @Test
    void delete_byAnotherUserOrAdmin_isForbidden() {
        assertThrows(BusinessException.class, () -> scheduleCommentService.deleteComment(100L, "stranger"));
        assertThrows(BusinessException.class, () -> scheduleCommentService.deleteComment(100L, "admin"));
        assertFalse(comment.isDeleted());
    }

    // 수정 화면 주소를 직접 쳐서 남의 한마디 원문을 읽는 것도 막는다.
    @Test
    void editForm_ofAnotherUsersComment_isForbidden() {
        assertThrows(BusinessException.class, () -> scheduleCommentService.getForEdit(100L, "stranger"));
        assertEquals(comment, scheduleCommentService.getForEdit(100L, "writer"));
    }

    // 작성자가 하드 삭제된 한마디(user == null)는 누구의 것도 아니다 - NPE 없이 거부.
    @Test
    void orphanedComment_cannotBeEditedByAnyone() {
        comment.setUser(null);

        assertThrows(BusinessException.class, () -> scheduleCommentService.updateComment(100L, "writer", "수정"));
        assertThrows(BusinessException.class, () -> scheduleCommentService.deleteComment(100L, "writer"));
        assertThrows(BusinessException.class, () -> scheduleCommentService.getForEdit(100L, "writer"));
    }

    @Test
    void deletedComment_isNotFoundEverywhere() {
        comment.setDeleted(true);

        assertThrows(BusinessException.class, () -> scheduleCommentService.updateComment(100L, "writer", "수정"));
        assertThrows(BusinessException.class, () -> scheduleCommentService.deleteComment(100L, "writer"));
        assertThrows(BusinessException.class, () -> scheduleCommentService.getForEdit(100L, "writer"));
        assertThrows(BusinessException.class, () -> scheduleCommentService.findForPermalink(100L));
    }

    @Test
    void delete_byOwner_isSoftDelete() {
        scheduleCommentService.deleteComment(100L, "writer");

        assertTrue(comment.isDeleted());
        assertTrue(comment.getDeletedAt() != null);
        verify(scheduleCommentRepository, never()).delete(any());
    }

    // ---- 수정 시 상태 변화 ----

    // 관리자가 "문제없음"으로 판결한 뒤 내용을 바꾸면 그 판결은 무효 - 안 그러면 판결받은 글을 욕설로 바꿔도 신고가 안 먹는다.
    @Test
    void update_withChangedContent_resetsReportClearedAndMarksEdited() {
        comment.setReportCleared(true);

        ScheduleCommentDto dto = scheduleCommentService.updateComment(100L, "writer", "내용 바꿈");

        assertEquals("내용 바꿈", comment.getContent());
        assertFalse(comment.isReportCleared());
        assertTrue(dto.isEdited());
    }

    @Test
    void update_withSameContent_changesNothing() {
        comment.setReportCleared(true);

        ScheduleCommentDto dto = scheduleCommentService.updateComment(100L, "writer", "  오늘 급식 맛있다  ");

        assertNull(comment.getUpdatedAt());
        assertTrue(comment.isReportCleared());
        assertFalse(dto.isEdited());
        verify(adminActionLogService, never()).log(any(), any(), any(), any());
    }

    // ---- 작성 ----

    @Test
    void create_stripsScriptsAndEventHandlers() {
        ScheduleCommentDto dto = create("writer", "<p onclick=\"steal()\">안녕</p><script>alert(1)</script>", false);

        assertFalse(dto.getContent().contains("<script"));
        assertFalse(dto.getContent().contains("onclick"));
        assertTrue(dto.getContent().contains("안녕"));
        assertTrue(dto.isMine());
    }

    @Test
    void create_rejectsEmptyContent_unlessPollIsAttached() {
        assertThrows(IllegalArgumentException.class, () -> create("writer", "   ", false));
        assertThrows(IllegalArgumentException.class, () -> create("writer", null, false));
        // 태그만 있고 글자·사진·영상이 없는 본문도 빈 내용이다.
        assertThrows(IllegalArgumentException.class, () -> create("writer", "<p><br></p>", false));

        assertEquals("", create("writer", "   ", true).getContent());
    }

    @Test
    void create_rejectsBannedWordsAndOverlongContent() {
        assertThrows(IllegalArgumentException.class, () -> create("writer", "<b>병</b>신", false));
        assertThrows(IllegalArgumentException.class, () -> create("writer", "가".repeat(50001), false));
        verify(scheduleCommentRepository, never()).save(any());
    }

    @Test
    void create_bySuspendedUser_isRejected() {
        doThrow(new IllegalArgumentException("커뮤니티 활동 제재 중")).when(userPenaltyService).assertCanComment(writer);

        assertThrows(IllegalArgumentException.class, () -> create("writer", "안녕", false));
        verify(scheduleCommentRepository, never()).save(any());
    }

    // 처음 조회되는 학교는 임시 이름으로 만들어 둔다 - 학교가 없다고 한마디 작성이 실패하면 안 된다.
    @Test
    void create_forUnknownSchool_createsSchoolRow() {
        when(schoolRepository.findBySdSchulCode("9999999")).thenReturn(Optional.empty());
        when(schoolRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        scheduleCommentService.createComment("B10", "9999999", DATE, "1", "2", "writer", "안녕", false);

        org.mockito.ArgumentCaptor<School> captor = org.mockito.ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(captor.capture());
        assertEquals("9999999", captor.getValue().getSdSchulCode());
        assertEquals("B10", captor.getValue().getAtptOfcdcScCode());
    }

    // ---- 목록 노출 ----

    @Test
    void blindComment_showsOriginalOnlyToAuthorAndAdmins() {
        comment.setBlind(true);

        assertEquals(BLIND_PLACEHOLDER, list("stranger", comment).get(0).getContent());
        assertEquals(BLIND_PLACEHOLDER, list(null, comment).get(0).getContent());
        assertEquals("오늘 급식 맛있다", list("writer", comment).get(0).getContent());
        assertEquals("오늘 급식 맛있다", list("admin", comment).get(0).getContent());
        assertEquals("오늘 급식 맛있다", list("super", comment).get(0).getContent());
    }

    @Test
    void commentsOfBlockedUsers_areHidden() {
        ScheduleComment fromStranger = comment(101L, stranger, "차단당한 사람의 글");
        ScheduleComment orphan = comment(102L, null, "탈퇴한 사람의 글");
        when(userBlockService.getBlockedUserIds("writer")).thenReturn(Set.of(2L));

        List<ScheduleCommentDto> visible = list("writer", comment, fromStranger, orphan);

        assertEquals(List.of(100L, 102L), visible.stream().map(ScheduleCommentDto::getId).toList());
    }

    // 탈퇴(소프트 삭제)·하드 삭제된 작성자는 닉네임을 감추고 프로필 링크도 걸지 않는다.
    @Test
    void commentOfWithdrawnAuthor_hidesNicknameAndProfileLink() {
        stranger.setDeleted(true);
        ScheduleComment withdrawn = comment(101L, stranger, "탈퇴 전에 쓴 글");
        ScheduleComment orphan = comment(102L, null, "하드 삭제된 사람의 글");

        List<ScheduleCommentDto> dtos = list("writer", withdrawn, orphan);

        for (ScheduleCommentDto dto : dtos) {
            assertEquals("탈퇴한 사용자", dto.getNickname());
            assertFalse(dto.isAuthorLinkable());
            assertFalse(dto.isMine());
        }
        assertNull(dtos.get(1).getAuthorId());
    }

    @Test
    void guest_seesNoPersonalFlags() {
        ScheduleCommentDto dto = list(null, comment).get(0);

        assertFalse(dto.isMine());
        assertFalse(dto.isLikedByMe());
        assertFalse(dto.isBookmarkedByMe());
        assertFalse(dto.isReportedByMe());
        assertNotEquals(BLIND_PLACEHOLDER, dto.getContent());
    }

    @Test
    void unknownUuid_isNotFound() {
        when(scheduleCommentRepository.findByUuid("nope")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> scheduleCommentService.resolveIdByUuid("nope"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
