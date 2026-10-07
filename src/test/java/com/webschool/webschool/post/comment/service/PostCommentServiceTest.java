package com.webschool.webschool.post.comment.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.comment.domain.PostComment;
import com.webschool.webschool.post.comment.dto.PostCommentDto;
import com.webschool.webschool.post.comment.repository.CommentBookmarkRepository;
import com.webschool.webschool.post.comment.repository.CommentLikeRepository;
import com.webschool.webschool.post.comment.repository.CommentReportRepository;
import com.webschool.webschool.post.comment.repository.PostCommentRepository;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.point.service.UserPointService;
import com.webschool.webschool.user.profile.service.UserBlockService;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPenaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 댓글 작성/수정/삭제/답변 채택의 권한 규칙 회귀 테스트. 화면은 본인 댓글에만 수정·삭제 버튼을 보여주지만
// API는 댓글 id만 바꿔 직접 호출할 수 있으므로, 소유권 검사는 이 서비스가 유일한 방어선이다.
@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {

    private static final Long POST_ID = 10L;

    @Mock private PostCommentRepository postCommentRepository;
    @Mock private CommentReportRepository commentReportRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private CommentBookmarkRepository commentBookmarkRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserPenaltyService userPenaltyService;
    @Mock private UserBlockService userBlockService;
    @Mock private AdminActionLogService adminActionLogService;
    @Mock private UserPointService userPointService;

    @InjectMocks private PostCommentService postCommentService;

    private User postAuthor;
    private User commenter;
    private User stranger;
    private User admin;
    private Post post;
    private PostComment comment;

    @BeforeEach
    void setUp() {
        postAuthor = user(1L, "asker", User.Role.ROLE_USER);
        commenter = user(2L, "helper", User.Role.ROLE_USER);
        stranger = user(3L, "stranger", User.Role.ROLE_USER);
        admin = user(4L, "admin", User.Role.ROLE_ADMIN);

        post = new Post();
        post.setId(POST_ID);
        post.setTitle("수학 문제 질문");
        post.setCategory(Post.Category.QNA);
        post.setVisibility(Post.Visibility.PUBLIC);
        post.setAuthor(postAuthor);
        lenient().when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        comment = comment(100L, commenter, "이렇게 풀면 돼");

        // 실제 DB에서는 저장 시점에 작성 시각이 채워진다 - 목 저장소에서는 직접 채워줘야 응답 DTO 변환이 된다.
        lenient().when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
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

    private PostComment comment(Long id, User author, String content) {
        PostComment c = new PostComment();
        c.setId(id);
        c.setPost(post);
        c.setAuthor(author);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        lenient().when(postCommentRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    // ---- 수정·삭제는 본인만 ----

    @Test
    void update_byAnotherUser_isForbidden() {
        assertThrows(BusinessException.class,
                () -> postCommentService.updateComment(100L, "stranger", "내가 바꿈"));
        assertEquals("이렇게 풀면 돼", comment.getContent());
    }

    @Test
    void delete_byAnotherUser_isForbidden() {
        assertThrows(BusinessException.class, () -> postCommentService.deleteComment(100L, "stranger"));
        // 게시글 작성자라도 남의 댓글은 못 지운다.
        assertThrows(BusinessException.class, () -> postCommentService.deleteComment(100L, "asker"));
        assertFalse(comment.isDeleted());
    }

    // 작성자가 하드 삭제된 댓글(author == null)은 누구의 것도 아니다 - NPE 없이 거부.
    @Test
    void orphanedComment_cannotBeEditedByAnyone() {
        comment.setAuthor(null);

        assertThrows(BusinessException.class, () -> postCommentService.updateComment(100L, "helper", "수정"));
        assertThrows(BusinessException.class, () -> postCommentService.deleteComment(100L, "helper"));
    }

    @Test
    void delete_isSoftDelete() {
        postCommentService.deleteComment(100L, "helper");

        assertTrue(comment.isDeleted());
        verify(postCommentRepository, never()).delete(any());
    }

    // 답글이 달린 댓글을 지우면 답글이 부모 없이 남는다.
    @Test
    void delete_withReplies_isRejected() {
        lenient().when(postCommentRepository.existsByParentComment_IdAndDeletedFalse(100L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> postCommentService.deleteComment(100L, "helper"));
        assertFalse(comment.isDeleted());
    }

    // "수정됨" 배지는 내용이 실제로 바뀔 때만 - 그대로 다시 저장하면 updatedAt이 찍히면 안 된다.
    @Test
    void update_withSameContent_doesNotMarkEdited() {
        comment.setReportCleared(true);

        postCommentService.updateComment(100L, "helper", "  이렇게 풀면 돼  ");

        assertNull(comment.getUpdatedAt());
        assertTrue(comment.isReportCleared());
    }

    // 내용이 바뀌면 관리자의 "문제없음" 판결은 무효 - 안 그러면 판결받은 뒤 내용을 바꿔 신고를 피할 수 있다.
    @Test
    void update_withNewContent_resetsReportCleared() {
        comment.setReportCleared(true);

        postCommentService.updateComment(100L, "helper", "다른 내용");

        assertEquals("다른 내용", comment.getContent());
        assertFalse(comment.isReportCleared());
        assertNotEquals(null, comment.getUpdatedAt());
    }

    // ---- 작성 ----

    @Test
    void create_onPrivatePost_onlyByItsAuthor() {
        post.setVisibility(Post.Visibility.PRIVATE);

        assertThrows(BusinessException.class,
                () -> postCommentService.createComment(POST_ID, "stranger", "보여요?", null));
        verify(postCommentRepository, never()).save(any());
    }

    @Test
    void create_onDeletedPost_isNotFound() {
        post.setDeleted(true);

        assertThrows(BusinessException.class,
                () -> postCommentService.createComment(POST_ID, "helper", "댓글", null));
        verify(postCommentRepository, never()).save(any());
    }

    // 커뮤니티 임시차단 제재 중이면 저장 전에 막힌다(포인트도 안 줌).
    @Test
    void create_whileSuspended_isRejectedBeforeSaving() {
        doThrow(new IllegalArgumentException("제재 중")).when(userPenaltyService).assertCanComment(stranger);

        assertThrows(IllegalArgumentException.class,
                () -> postCommentService.createComment(POST_ID, "stranger", "댓글", null));
        verify(postCommentRepository, never()).save(any());
        verify(userPointService, never()).award(any(), anyInt(), anyString());
    }

    // 익명 게시판은 작성자가 누군지 가려져 있어 차단 검사를 하지 않는다 - 검사하면 "댓글이 막혔다 = 나를
    // 차단한 그 사람이 쓴 글"이라는 추론으로 익명이 깨진다.
    @Test
    void create_blockCheckIsSkippedOnAnonymousBoard() {
        postCommentService.createComment(POST_ID, "stranger", "댓글", null);
        verify(userBlockService).assertNotBlocked(stranger, postAuthor);

        post.setCategory(Post.Category.ANONYMOUS);
        postCommentService.createComment(POST_ID, "helper", "댓글", null);
        verify(userBlockService, never()).assertNotBlocked(eq(commenter), any());
    }

    @Test
    void create_replyRules() {
        PostComment reply = comment(101L, stranger, "답글");
        reply.setParentComment(comment);
        // 답글의 답글(2단계)은 안 된다.
        assertThrows(IllegalArgumentException.class,
                () -> postCommentService.createComment(POST_ID, "asker", "또 답글", 101L));

        // 다른 게시글의 댓글을 부모로 지정할 수 없다.
        Post otherPost = new Post();
        otherPost.setId(99L);
        PostComment foreign = comment(102L, stranger, "남의 글 댓글");
        foreign.setPost(otherPost);
        assertThrows(BusinessException.class,
                () -> postCommentService.createComment(POST_ID, "asker", "답글", 102L));

        comment.setDeleted(true);
        assertThrows(IllegalArgumentException.class,
                () -> postCommentService.createComment(POST_ID, "asker", "답글", 100L));

        verify(postCommentRepository, never()).save(any());
    }

    @Test
    void create_validatesContent() {
        assertThrows(IllegalArgumentException.class,
                () -> postCommentService.createComment(POST_ID, "helper", "   ", null));
        assertThrows(IllegalArgumentException.class,
                () -> postCommentService.createComment(POST_ID, "helper", "가".repeat(501), null));
        verify(postCommentRepository, never()).save(any());
    }

    // ---- 답변 채택 ----

    @Test
    void accept_onlyByQuestionAuthor() {
        assertThrows(BusinessException.class, () -> postCommentService.acceptAnswer(100L, "stranger"));
        // 답변을 쓴 본인이 자기 답변을 채택 처리하는 경로(포인트 15점).
        assertThrows(BusinessException.class, () -> postCommentService.acceptAnswer(100L, "helper"));

        assertFalse(comment.isAccepted());
        verify(userPointService, never()).award(any(), anyInt(), anyString());
    }

    // 질문자가 자기 댓글을 채택해 스스로 포인트를 받는 경로.
    @Test
    void accept_ownComment_isForbidden() {
        PostComment own = comment(103L, postAuthor, "제가 풀었어요");

        assertThrows(BusinessException.class, () -> postCommentService.acceptAnswer(103L, "asker"));
        assertFalse(own.isAccepted());
    }

    @Test
    void accept_onlyOnQnaBoardAndTopLevelComments() {
        post.setCategory(Post.Category.FREE);
        assertThrows(IllegalArgumentException.class, () -> postCommentService.acceptAnswer(100L, "asker"));

        post.setCategory(Post.Category.QNA);
        PostComment reply = comment(104L, stranger, "답글");
        reply.setParentComment(comment);
        assertThrows(IllegalArgumentException.class, () -> postCommentService.acceptAnswer(104L, "asker"));
    }

    // 채택은 글마다 최대 1개 - 새로 채택하면 기존 채택이 풀린다.
    @Test
    void accept_replacesPreviousAcceptedAnswer() {
        PostComment previous = comment(105L, stranger, "예전 답변");
        previous.setAccepted(true);
        lenient().when(postCommentRepository.findByPost_IdAndAcceptedTrue(POST_ID)).thenReturn(Optional.of(previous));

        assertTrue(postCommentService.acceptAnswer(100L, "asker"));

        assertTrue(comment.isAccepted());
        assertFalse(previous.isAccepted());
        verify(userPointService).award(commenter, UserPointService.ANSWER_ACCEPTED, "답변 채택됨");
    }

    @Test
    void accept_again_togglesOffWithoutAwarding() {
        comment.setAccepted(true);

        assertFalse(postCommentService.acceptAnswer(100L, "asker"));

        assertFalse(comment.isAccepted());
        verify(userPointService, never()).award(any(), anyInt(), anyString());
    }

    // ---- 목록 ----

    // 블라인드된 댓글 원문은 작성자 본인과 관리자만 본다 - 남에게는 안내 문구로 치환.
    @Test
    void blindComment_contentIsHiddenFromOthers() {
        comment.setBlind(true);
        lenient().when(postCommentRepository.findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(POST_ID))
                .thenReturn(List.of(comment));

        assertNotEquals("이렇게 풀면 돼", contentSeenBy("stranger"));
        assertNotEquals("이렇게 풀면 돼", contentSeenBy(null));
        assertEquals("이렇게 풀면 돼", contentSeenBy("helper"));
        assertEquals("이렇게 풀면 돼", contentSeenBy("admin"));
    }

    private String contentSeenBy(String username) {
        List<PostCommentDto> comments = postCommentService.getComments(POST_ID, username);
        return comments.get(0).getContent();
    }

    // 탈퇴한 작성자는 닉네임이 가려지고 프로필 링크도 걸리지 않는다.
    @Test
    void withdrawnAuthor_isShownAsWithdrawnUser() {
        commenter.setDeleted(true);
        lenient().when(postCommentRepository.findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(POST_ID))
                .thenReturn(List.of(comment));

        PostCommentDto dto = postCommentService.getComments(POST_ID, "stranger").get(0);

        assertEquals("탈퇴한 사용자", dto.getNickname());
        assertFalse(dto.isAuthorLinkable());
    }
}
