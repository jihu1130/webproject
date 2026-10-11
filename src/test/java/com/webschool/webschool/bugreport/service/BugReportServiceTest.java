package com.webschool.webschool.bugreport.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.bugreport.domain.BugReport;
import com.webschool.webschool.bugreport.domain.BugReportAttachment;
import com.webschool.webschool.bugreport.domain.InquiryReply;
import com.webschool.webschool.bugreport.dto.BugReportDto;
import com.webschool.webschool.bugreport.repository.BugReportAttachmentRepository;
import com.webschool.webschool.bugreport.repository.BugReportRepository;
import com.webschool.webschool.bugreport.repository.InquiryReplyRepository;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.global.mail.MailService;
import com.webschool.webschool.global.upload.FileUploadService;
import com.webschool.webschool.global.upload.UploadedFileDto;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 문의(버그 리포트) 접수/답변/해결/삭제 회귀 테스트. 접수는 비로그인으로도 열려 있어(permitAll) 입력 검증이
// 전부 이 서비스에 달려 있고, 답변·해결·삭제는 화면 접근 제한과 별개로 서비스가 권한을 한 번 더 확인한다.
@ExtendWith(MockitoExtension.class)
class BugReportServiceTest {

    @Mock private BugReportRepository bugReportRepository;
    @Mock private BugReportAttachmentRepository bugReportAttachmentRepository;
    @Mock private InquiryReplyRepository inquiryReplyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminActionLogService adminActionLogService;
    @Mock private FileUploadService fileUploadService;
    @Mock private NotificationService notificationService;
    @Mock private MailService mailService;

    @InjectMocks private BugReportService bugReportService;

    private User reporter;
    private BugReport memberReport;
    private BugReport guestReport;

    @BeforeEach
    void setUp() {
        reporter = user(1L, "reporter", User.Role.ROLE_USER);
        user(2L, "stranger", User.Role.ROLE_USER);
        user(3L, "super", User.Role.ROLE_SUPER_ADMIN);
        user(4L, "inquiryAdmin", User.Role.ROLE_ADMIN).setCanManageBugReports(true);
        user(5L, "otherAdmin", User.Role.ROLE_ADMIN);

        memberReport = report(10L, "로그인이 안 돼요", reporter, null, null);
        guestReport = report(11L, "가입이 안 돼요", null, "지나가던 학생", "guest@example.com");

        lenient().when(bugReportRepository.save(any())).thenAnswer(invocation -> {
            BugReport saved = invocation.getArgument(0);
            saved.setId(99L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        lenient().when(fileUploadService.isImageOrVideoExtension(anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).matches(".*\\.(png|jpg|mp4)$"));
        lenient().when(fileUploadService.store(any())).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            return UploadedFileDto.builder().url("/uploads/" + file.getOriginalFilename())
                    .originalFilename(file.getOriginalFilename()).kind("image").build();
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

    private BugReport report(Long id, String title, User owner, String guestNickname, String email) {
        BugReport r = new BugReport();
        r.setId(id);
        r.setTitle(title);
        r.setContent("내용");
        r.setReporter(owner);
        r.setReporterNickname(guestNickname);
        r.setContactEmail(email);
        r.setCreatedAt(LocalDateTime.now());
        lenient().when(bugReportRepository.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    private MultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[]{1, 2, 3});
    }

    private BugReport submitted() {
        ArgumentCaptor<BugReport> captor = ArgumentCaptor.forClass(BugReport.class);
        verify(bugReportRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---- 접수: 비로그인 ----

    @Test
    void guestSubmit_requiresContactEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport(null, "BUG", "제목", "내용", "닉", "  ", null));
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport(null, "BUG", "제목", "내용", "닉", null, null));
        verify(bugReportRepository, never()).save(any());
    }

    @Test
    void guestSubmit_storesTrimmedNicknameAndEmail() {
        bugReportService.submitReport(null, null, " 제목 ", " 내용 ", "  지나가던 학생 ", " guest@example.com ", null);

        BugReport saved = submitted();
        assertNull(saved.getReporter());
        assertEquals("지나가던 학생", saved.getReporterNickname());
        assertEquals("guest@example.com", saved.getContactEmail());
        assertEquals("제목", saved.getTitle());
        // 유형을 안 고르면 버그로 접수된다.
        assertEquals(BugReport.Category.BUG, saved.getCategory());
    }

    @Test
    void submit_rejectsMalformedEmail() {
        for (String email : List.of("no-at-sign", "a@b", "a b@c.com", "@c.com")) {
            assertThrows(IllegalArgumentException.class,
                    () -> bugReportService.submitReport(null, "BUG", "제목", "내용", null, email, null), email);
        }
    }

    // DB 컬럼 길이(닉네임 30자, 이메일 100자)를 넘는 값은 저장 단계에서 500 오류가 나므로 미리 거른다.
    // 비로그인으로 누구나 보낼 수 있는 입력이라 화면의 maxlength에 기대면 안 된다.
    @Test
    void guestSubmit_rejectsNicknameOrEmailLongerThanColumn() {
        assertThrows(IllegalArgumentException.class, () -> bugReportService.submitReport(
                null, "BUG", "제목", "내용", "가".repeat(31), "guest@example.com", null));
        assertThrows(IllegalArgumentException.class, () -> bugReportService.submitReport(
                null, "BUG", "제목", "내용", "닉", "a".repeat(95) + "@b.com", null));
        verify(bugReportRepository, never()).save(any());
    }

    // ---- 접수: 로그인 ----

    // 로그인 제출은 계정에 연결되고, 폼에 닉네임을 끼워 보내도 무시한다(남의 이름으로 문의 못 하게).
    @Test
    void memberSubmit_linksAccount_andIgnoresNicknameField() {
        bugReportService.submitReport("reporter", "SUGGESTION", "제목", "내용", "관리자", null, null);

        BugReport saved = submitted();
        assertEquals(reporter, saved.getReporter());
        assertNull(saved.getReporterNickname());
        assertNull(saved.getContactEmail());
        assertEquals(BugReport.Category.SUGGESTION, saved.getCategory());
    }

    @Test
    void submit_rejectsInvalidTitleContentOrCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport("reporter", "BUG", " ", "내용", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport("reporter", "BUG", "가".repeat(101), "내용", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport("reporter", "BUG", "제목", "가".repeat(2001), null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport("reporter", "BUG", "제목", "씨발", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport("reporter", "HACK", "제목", "내용", null, null, null));
        verify(bugReportRepository, never()).save(any());
    }

    // ---- 접수: 첨부 ----

    // 사진·영상이 아닌 파일은 저장소에 쓰기 전에 걸러야 한다 - 하나라도 섞여 있으면 아무것도 저장하지 않는다.
    @Test
    void submit_withNonMediaAttachment_storesNothing() {
        List<MultipartFile> files = List.of(file("a.png"), file("evil.html"));

        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport(null, "BUG", "제목", "내용", null, "g@example.com", files));

        verify(fileUploadService, never()).store(any());
        verify(bugReportRepository, never()).save(any());
    }

    @Test
    void submit_withMoreThanFiveAttachments_isRejected() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            files.add(file("p" + i + ".png"));
        }

        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.submitReport("reporter", "BUG", "제목", "내용", null, null, files));
        verify(fileUploadService, never()).store(any());
    }

    // 파일을 안 고른 입력칸은 빈 파일로 넘어온다 - 개수에 세지 않고 건너뛴다.
    @Test
    void submit_skipsEmptyFileParts_andKeepsOrder() {
        List<MultipartFile> files = new ArrayList<>(List.of(
                file("first.png"),
                new MockMultipartFile("files", "", "application/octet-stream", new byte[0]),
                file("second.mp4")));

        bugReportService.submitReport("reporter", "BUG", "제목", "내용", null, null, files);

        ArgumentCaptor<BugReportAttachment> captor = ArgumentCaptor.forClass(BugReportAttachment.class);
        verify(bugReportAttachmentRepository, times(2)).save(captor.capture());
        assertEquals(List.of("first.png", "second.mp4"),
                captor.getAllValues().stream().map(BugReportAttachment::getOriginalFilename).toList());
        assertEquals(List.of(0, 1), captor.getAllValues().stream().map(BugReportAttachment::getSortOrder).toList());
    }

    // ---- 관리자 답변 ----

    @Test
    void adminActions_withoutPermission_areForbidden() {
        for (String username : List.of("otherAdmin", "stranger")) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> bugReportService.addReply(10L, username, "답변"));
            assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
            assertThrows(BusinessException.class, () -> bugReportService.resolve(10L, username));
            assertThrows(BusinessException.class, () -> bugReportService.delete(10L, username));
        }
        verify(inquiryReplyRepository, never()).save(any());
        verify(bugReportRepository, never()).delete(any());
        assertFalse(memberReport.isResolved());
    }

    @Test
    void reply_toMember_sendsInAppNotification_notMail() {
        memberReport.setContactEmail("reporter@example.com");

        bugReportService.addReply(10L, "inquiryAdmin", " 확인했습니다 ");

        ArgumentCaptor<InquiryReply> captor = ArgumentCaptor.forClass(InquiryReply.class);
        verify(inquiryReplyRepository).save(captor.capture());
        assertEquals("확인했습니다", captor.getValue().getContent());
        assertEquals("inquiryAdmin", captor.getValue().getAdminUsername());
        assertNull(captor.getValue().getUserUsername());
        verify(notificationService).notify(eq(reporter), eq(Notification.Type.INQUIRY_REPLY), anyString(),
                eq("/mypage/inquiries"));
        verify(mailService, never()).sendInquiryReply(anyString(), anyString());
    }

    @Test
    void reply_toGuest_sendsMailToContactEmail() {
        bugReportService.addReply(11L, "super", "확인했습니다");

        verify(mailService).sendInquiryReply("guest@example.com", "확인했습니다");
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString());
    }

    @Test
    void reply_rejectsBlankBannedOrOverlongContent() {
        assertThrows(IllegalArgumentException.class, () -> bugReportService.addReply(10L, "super", "  "));
        assertThrows(IllegalArgumentException.class, () -> bugReportService.addReply(10L, "super", null));
        assertThrows(IllegalArgumentException.class, () -> bugReportService.addReply(10L, "super", "지랄"));
        // 답변 컬럼은 2000자 - 넘으면 저장 단계에서 500 오류가 난다.
        assertThrows(IllegalArgumentException.class, () -> bugReportService.addReply(10L, "super", "가".repeat(2001)));
        assertThrows(IllegalArgumentException.class,
                () -> bugReportService.addUserReply(10L, "reporter", "가".repeat(2001)));
        verify(inquiryReplyRepository, never()).save(any());
    }

    // ---- 작성자 재답장 ----

    @Test
    void userReply_onSomeoneElsesOrGuestInquiry_isForbidden() {
        assertThrows(BusinessException.class, () -> bugReportService.addUserReply(10L, "stranger", "끼어들기"));
        assertThrows(BusinessException.class, () -> bugReportService.addUserReply(11L, "stranger", "끼어들기"));
        verify(inquiryReplyRepository, never()).save(any());
    }

    @Test
    void userReply_onResolvedInquiry_isRejected() {
        memberReport.setResolved(true);

        assertThrows(IllegalArgumentException.class, () -> bugReportService.addUserReply(10L, "reporter", "추가 질문"));
        verify(inquiryReplyRepository, never()).save(any());
    }

    @Test
    void userReply_byOwner_isSavedAsUserReply() {
        bugReportService.addUserReply(10L, "reporter", " 추가 질문 ");

        ArgumentCaptor<InquiryReply> captor = ArgumentCaptor.forClass(InquiryReply.class);
        verify(inquiryReplyRepository).save(captor.capture());
        assertEquals("reporter", captor.getValue().getUserUsername());
        assertNull(captor.getValue().getAdminUsername());
        assertEquals("추가 질문", captor.getValue().getContent());
    }

    // ---- 해결 / 삭제 ----

    @Test
    void resolve_toggles_andClearsResolvedAtOnReopen() {
        bugReportService.resolve(10L, "super");
        assertTrue(memberReport.isResolved());
        assertTrue(memberReport.getResolvedAt() != null);

        bugReportService.resolve(10L, "super");
        assertFalse(memberReport.isResolved());
        assertNull(memberReport.getResolvedAt());
    }

    // 첨부·답변 행을 먼저 지워야 FK 위반 없이 문의를 지울 수 있다.
    @Test
    void delete_removesAttachmentsAndRepliesBeforeReport() {
        List<BugReportAttachment> attachments = List.of(new BugReportAttachment());
        List<InquiryReply> replies = List.of(new InquiryReply());
        when(bugReportAttachmentRepository.findByBugReport_IdOrderBySortOrderAsc(10L)).thenReturn(attachments);
        when(inquiryReplyRepository.findByBugReport_IdAndDeletedFalseOrderByCreatedAtAsc(10L)).thenReturn(replies);

        bugReportService.delete(10L, "inquiryAdmin");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                bugReportAttachmentRepository, inquiryReplyRepository, bugReportRepository);
        order.verify(bugReportAttachmentRepository).deleteAll(attachments);
        order.verify(inquiryReplyRepository).deleteAll(replies);
        order.verify(bugReportRepository).delete(memberReport);
    }

    // ---- 조회 ----

    // "내 문의"에는 내 계정으로 낸 문의만 - 남의 문의나 익명 문의가 섞이면 연락 이메일이 노출된다.
    @Test
    void myInquiries_containsOnlyOwnReports() {
        BugReport strangers = report(12L, "남의 문의", userRepository.findByUsername("stranger").orElseThrow(), null, null);
        when(bugReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(memberReport, guestReport, strangers));

        Page<BugReportDto> mine = bugReportService.getMyInquiries("reporter", 0, 10);

        assertEquals(List.of(10L), mine.getContent().stream().map(BugReportDto::getId).toList());
    }

    @Test
    void adminList_filtersByCategoryResolvedAndKeyword() {
        guestReport.setCategory(BugReport.Category.ACCOUNT);
        guestReport.setResolved(true);
        when(bugReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(memberReport, guestReport));

        assertEquals(2, bugReportService.getList(0, 10).getTotalElements());
        assertEquals(List.of(11L), ids(bugReportService.getList(null, "ACCOUNT", null, 0, 10)));
        assertEquals(List.of(10L), ids(bugReportService.getList(null, null, false, 0, 10)));
        // 키워드는 작성자 표시 이름, 이메일, 제목에서 찾는다(대소문자 무시).
        assertEquals(List.of(11L), ids(bugReportService.getList("GUEST@", null, null, 0, 10)));
        assertEquals(List.of(10L), ids(bugReportService.getList("reporter닉", " ", null, 0, 10)));
        assertEquals(List.of(), ids(bugReportService.getList("없는말", null, null, 0, 10)));
    }

    private List<Long> ids(Page<BugReportDto> page) {
        return page.getContent().stream().map(BugReportDto::getId).toList();
    }

    @Test
    void detail_showsReporterDisplayAndReplyAuthors() {
        InquiryReply fromAdmin = reply("super", null, "확인 중입니다");
        InquiryReply fromUser = reply(null, "reporter", "감사합니다");
        when(inquiryReplyRepository.findByBugReport_IdAndDeletedFalseOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(fromAdmin, fromUser));

        BugReportDto member = bugReportService.getDetail(10L);
        assertEquals("reporter닉", member.getReporterDisplay());
        // 답변한 관리자의 아이디는 사용자에게 보이지 않는다.
        assertEquals("관리자", member.getReplies().get(0).getAuthorDisplay());
        assertTrue(member.getReplies().get(0).isFromAdmin());
        assertEquals("reporter닉", member.getReplies().get(1).getAuthorDisplay());

        assertEquals("지나가던 학생", bugReportService.getDetail(11L).getReporterDisplay());
        guestReport.setReporterNickname(null);
        assertEquals("익명", bugReportService.getDetail(11L).getReporterDisplay());
    }

    private InquiryReply reply(String adminUsername, String userUsername, String content) {
        InquiryReply r = new InquiryReply();
        r.setAdminUsername(adminUsername);
        r.setUserUsername(userUsername);
        r.setContent(content);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    void unknownReport_isNotFound() {
        when(bugReportRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> bugReportService.getDetail(404L));
        assertThrows(BusinessException.class, () -> bugReportService.addReply(404L, "super", "답변"));
        assertThrows(BusinessException.class, () -> bugReportService.addUserReply(404L, "reporter", "답장"));
    }
}
