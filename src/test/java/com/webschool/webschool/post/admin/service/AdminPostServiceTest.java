package com.webschool.webschool.post.admin.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.admin.dto.AdminCommentReportSummaryDto;
import com.webschool.webschool.post.admin.dto.AdminPostDetailDto;
import com.webschool.webschool.post.admin.dto.AdminPostSummaryDto;
import com.webschool.webschool.post.comment.domain.PostComment;
import com.webschool.webschool.post.comment.repository.PostCommentRepository;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostReport;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.service.PostImageService;
import com.webschool.webschool.user.domain.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 관리자 신고 처리(블라인드/문제없음/삭제/복구)의 상태 전이와 목록 필터 회귀 테스트.
// 이 서비스는 권한을 직접 확인하지 않는다 - 접근 제한은 AdminAccessInterceptor의 몫이다.
@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostImageService postImageService;
    @Mock private NotificationService notificationService;
    @Mock private AdminActionLogService adminActionLogService;

    @InjectMocks private AdminPostService adminPostService;

    private User author;
    private Post post;
    private PostComment comment;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(1L);
        author.setUsername("writer");
        author.setNickname("글쓴이");

        post = post(10L, "수학 문제 질문", author, LocalDateTime.of(2026, 10, 5, 12, 0));
        comment = comment(100L, post, author, "이렇게 풀면 돼");
    }

    private Post post(Long id, String title, User postAuthor, LocalDateTime createdAt) {
        Post p = new Post();
        p.setId(id);
        p.setUuid("uuid-" + id);
        p.setTitle(title);
        p.setContent("본문");
        p.setCategory(Post.Category.QNA);
        p.setAuthor(postAuthor);
        p.setCreatedAt(createdAt);
        lenient().when(postRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private PostComment comment(Long id, Post parent, User commentAuthor, String content) {
        PostComment c = new PostComment();
        c.setId(id);
        c.setPost(parent);
        c.setAuthor(commentAuthor);
        c.setContent(content);
        c.setCreatedAt(parent.getCreatedAt());
        lenient().when(postCommentRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private PostReport report(User reporter, String reason, LocalDateTime createdAt) {
        PostReport r = new PostReport();
        r.setPost(post);
        r.setReporter(reporter);
        r.setReason(reason);
        r.setCreatedAt(createdAt);
        return r;
    }

    // ---- 게시글: 블라인드 / 문제없음 ----

    // 다시 블라인드한다는 건 "문제없음" 판결을 뒤집는 것 - 판결이 남아 있으면 재신고가 계속 무시된다.
    @Test
    void blind_resetsClearedVerdict_andNotifiesAuthor() {
        post.setReportCleared(true);

        adminPostService.setBlind(10L, true);

        assertTrue(post.isBlind());
        assertFalse(post.isReportCleared());
        verify(notificationService).notify(eq(author), eq(Notification.Type.REPORT_ACTION),
                contains("블라인드 처리되었습니다"), eq("/posts/uuid-10"));
        verify(adminActionLogService).log("POST", 10L, "BLIND", "수학 문제 질문");
    }

    @Test
    void unblind_keepsVerdictUntouched() {
        post.setBlind(true);
        post.setReportCleared(true);

        adminPostService.setBlind(10L, false);

        assertFalse(post.isBlind());
        assertTrue(post.isReportCleared());
        verify(adminActionLogService).log("POST", 10L, "UNBLIND", "수학 문제 질문");
    }

    @Test
    void clearReport_unblinds_andUnclearOnlyRevertsVerdict() {
        post.setBlind(true);
        post.setReportCount(3);

        adminPostService.clearReport(10L);
        assertTrue(post.isReportCleared());
        assertFalse(post.isBlind());

        adminPostService.unclearReport(10L);
        assertFalse(post.isReportCleared());
        // 철회해도 신고 횟수는 남고 블라인드가 자동으로 다시 걸리지는 않는다.
        assertEquals(3, post.getReportCount());
        assertFalse(post.isBlind());
    }

    // 작성자가 하드 삭제된 글(author == null)도 처리할 수 있어야 한다.
    @Test
    void actionsOnPostOfDeletedAuthor_doNotFail() {
        post.setAuthor(null);

        adminPostService.setBlind(10L, true);
        adminPostService.clearReport(10L);

        assertTrue(post.isReportCleared());
        assertEquals("탈퇴한 사용자", adminPostService.getPostDetail(10L).getAuthorNickname());
        assertNull(adminPostService.getPostDetail(10L).getAuthorUsername());
    }

    // ---- 게시글: 삭제 / 복구 ----

    @Test
    void deleteAndRestorePost_areSoftAndReversible() {
        adminPostService.deletePost(10L);
        assertTrue(post.isDeleted());
        assertTrue(post.getDeletedAt() != null);
        verify(postRepository, never()).delete(any());

        adminPostService.restorePost(10L);
        assertFalse(post.isDeleted());
        assertNull(post.getDeletedAt());
    }

    @Test
    void unknownPostOrComment_isNotFound() {
        when(postRepository.findById(404L)).thenReturn(Optional.empty());
        when(postCommentRepository.findById(404L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.POST_NOT_FOUND,
                assertThrows(BusinessException.class, () -> adminPostService.setBlind(404L, true)).getErrorCode());
        assertThrows(BusinessException.class, () -> adminPostService.getPostDetail(404L));
        assertThrows(BusinessException.class, () -> adminPostService.deletePost(404L));
        assertEquals(ErrorCode.COMMENT_NOT_FOUND,
                assertThrows(BusinessException.class, () -> adminPostService.deleteComment(404L)).getErrorCode());
        assertThrows(BusinessException.class, () -> adminPostService.setCommentBlind(404L, true));
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString());
    }

    // ---- 댓글 ----

    @Test
    void commentBlind_resetsVerdict_andLinksToParentPost() {
        comment.setReportCleared(true);

        adminPostService.setCommentBlind(100L, true);

        assertTrue(comment.isBlind());
        assertFalse(comment.isReportCleared());
        verify(notificationService).notify(eq(author), eq(Notification.Type.REPORT_ACTION),
                contains("블라인드 처리되었습니다"), eq("/posts/uuid-10"));
    }

    @Test
    void commentClearReport_unblinds_andUnclearReverts() {
        comment.setBlind(true);

        adminPostService.clearCommentReport(100L);
        assertTrue(comment.isReportCleared());
        assertFalse(comment.isBlind());

        adminPostService.unclearCommentReport(100L);
        assertFalse(comment.isReportCleared());
    }

    @Test
    void deleteAndRestoreComment_areSoftAndReversible() {
        adminPostService.deleteComment(100L);
        assertTrue(comment.isDeleted());
        assertTrue(comment.getDeletedAt() != null);
        verify(postCommentRepository, never()).delete(any());

        adminPostService.restoreComment(100L);
        assertFalse(comment.isDeleted());
        assertNull(comment.getDeletedAt());
    }

    // ---- 목록 ----

    @Test
    void reportedPosts_filterByDateRangeAndKeyword() {
        User other = new User();
        other.setId(2L);
        other.setNickname("Helper");
        Post older = post(11L, "급식 후기", other, LocalDateTime.of(2026, 9, 20, 9, 0));
        when(postRepository.findReportedOrBlindPosts()).thenReturn(List.of(post, older));

        assertEquals(List.of(10L, 11L), postIds(adminPostService.getReportedPosts(null, null, null)));
        // 날짜 범위는 양 끝을 포함한다.
        assertEquals(List.of(10L), postIds(adminPostService.getReportedPosts(null,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5))));
        assertEquals(List.of(11L), postIds(adminPostService.getReportedPosts(null, null, LocalDate.of(2026, 10, 4))));
        // 키워드는 제목과 작성자 닉네임에서 찾는다(대소문자 무시).
        assertEquals(List.of(11L), postIds(adminPostService.getReportedPosts("helper", null, null)));
        assertEquals(List.of(10L), postIds(adminPostService.getReportedPosts("수학", null, null)));
        assertEquals(List.of(), postIds(adminPostService.getReportedPosts("없는말", null, null)));
    }

    private List<Long> postIds(List<AdminPostSummaryDto> dtos) {
        return dtos.stream().map(AdminPostSummaryDto::getId).toList();
    }

    @Test
    void postSummary_showsLatestReportTimeAndReportedCommentCount() {
        when(postRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(post));
        when(postReportRepository.findByPost_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(
                report(author, "최근 신고", LocalDateTime.of(2026, 10, 7, 15, 30)),
                report(author, "예전 신고", LocalDateTime.of(2026, 10, 6, 8, 0))));
        when(postCommentRepository.countReportedOrBlindByPostId(10L)).thenReturn(2L);

        AdminPostSummaryDto dto = adminPostService.getAllPosts(" ").get(0);

        assertEquals("10.07 15:30", dto.getLatestReportAt());
        assertEquals(2, dto.getReportedCommentCount());
        assertEquals("글쓴이", dto.getAuthorNickname());
    }

    @Test
    void commentLists_filterByContentOrNickname_andSurviveDeletedAuthor() {
        PostComment orphan = comment(101L, post, null, "탈퇴한 사람의 댓글");
        when(postCommentRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(comment, orphan));

        List<AdminCommentReportSummaryDto> all = adminPostService.getAllComments(null);
        assertEquals(2, all.size());
        assertEquals("탈퇴한 사용자", all.get(1).getAuthorNickname());
        assertNull(all.get(1).getAuthorId());
        assertEquals(10L, all.get(1).getPostId());

        assertEquals(List.of(100L), adminPostService.getAllComments("글쓴이").stream()
                .map(AdminCommentReportSummaryDto::getId).toList());
        assertEquals(List.of(101L), adminPostService.getAllComments("탈퇴한 사람").stream()
                .map(AdminCommentReportSummaryDto::getId).toList());
    }

    // ---- 상세 ----

    // 신고자가 하드 삭제되면 reporter가 null이 된다 - 신고 내역은 남고 화면도 깨지면 안 된다.
    @Test
    void detail_includesReportsCommentsAndDeletedState() {
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.of(2026, 10, 8, 10, 0));
        comment.setDeleted(true);
        when(postReportRepository.findByPost_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(
                report(author, "욕설", LocalDateTime.of(2026, 10, 7, 15, 30)),
                report(null, null, LocalDateTime.of(2026, 10, 6, 8, 0))));
        when(postCommentRepository.findByPost_IdOrderByCreatedAtAsc(10L)).thenReturn(List.of(comment));

        AdminPostDetailDto detail = adminPostService.getPostDetail(10L);

        // 삭제된 글도 관리자는 id로 조회할 수 있다(복구하려면 내용을 봐야 한다).
        assertTrue(detail.isDeleted());
        assertEquals("10.08 10:00", detail.getDeletedAt());
        assertEquals("writer", detail.getReports().get(0).getReporterUsername());
        assertEquals("탈퇴한 사용자", detail.getReports().get(1).getReporterNickname());
        assertNull(detail.getReports().get(1).getReporterId());
        // 삭제된 댓글도 상세에는 그대로 나온다.
        assertEquals(1, detail.getComments().size());
        assertTrue(detail.getComments().get(0).isDeleted());
    }
}
