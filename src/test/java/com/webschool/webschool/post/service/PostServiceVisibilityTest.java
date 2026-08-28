package com.webschool.webschool.post.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPenaltyService;
import com.webschool.webschool.user.service.UserPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// 게시글 공개범위(Post.Visibility - PUBLIC/UNLISTED/PRIVATE) 접근 제어 회귀 테스트.
// PostService.getDetail()의 PRIVATE 차단 로직은 지금까지 브라우저로 수동 확인만 됐고
// 자동 테스트가 없었다(전체 기능 QA 중 발견, todo.md "고도화 후보" 참고) - 이 파일은
// 그 수동 검증(본인/관리자는 열람 가능, 제3자/비로그인은 "게시물을 찾을 수 없습니다")을
// 그대로 자동화한 것. UNLISTED/PUBLIC은 링크만 있으면 누구나 상세를 열 수 있다는 것도 함께 검증.
@ExtendWith(MockitoExtension.class)
class PostServiceVisibilityTest {

    @Mock private PostRepository postRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostBookmarkRepository postBookmarkRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserPenaltyService userPenaltyService;
    @Mock private UserPointService userPointService;
    @Mock private AdminActionLogService adminActionLogService;

    @InjectMocks private PostService postService;

    private User author;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(1L);
        author.setUsername("author");
        author.setNickname("author");
        author.setRole(User.Role.ROLE_USER);
    }

    private Post buildPost(Post.Visibility visibility) {
        Post post = new Post();
        post.setId(10L);
        post.setUuid("uuid-10");
        post.setTitle("title");
        post.setContent("content");
        post.setAuthor(author);
        post.setCategory(Post.Category.FREE);
        post.setVisibility(visibility);
        post.setCreatedAt(LocalDateTime.now());
        return post;
    }

    @Test
    void privatePost_blocksThirdPartyUser() {
        Post post = buildPost(Post.Visibility.PRIVATE);
        User stranger = new User();
        stranger.setUsername("stranger");
        stranger.setRole(User.Role.ROLE_USER);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(stranger));

        assertThrows(IllegalArgumentException.class,
                () -> postService.getDetail(10L, "stranger", false));
    }

    @Test
    void privatePost_blocksAnonymousVisitor() {
        Post post = buildPost(Post.Visibility.PRIVATE);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThrows(IllegalArgumentException.class,
                () -> postService.getDetail(10L, null, false));
    }

    @Test
    void privatePost_allowsOwner() {
        Post post = buildPost(Post.Visibility.PRIVATE);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertDoesNotThrow(() -> postService.getDetail(10L, "author", false));
    }

    @Test
    void privatePost_allowsAdmin() {
        Post post = buildPost(Post.Visibility.PRIVATE);
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ROLE_ADMIN);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertDoesNotThrow(() -> postService.getDetail(10L, "admin", false));
    }

    @Test
    void unlistedPost_isOpenToAnyoneWithTheLink() {
        Post post = buildPost(Post.Visibility.UNLISTED);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertDoesNotThrow(() -> postService.getDetail(10L, "stranger", false));
    }

    @Test
    void publicPost_isOpenToAnyone() {
        Post post = buildPost(Post.Visibility.PUBLIC);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertDoesNotThrow(() -> postService.getDetail(10L, null, false));
    }
}
