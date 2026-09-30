package com.webschool.webschool.post.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.dto.PostDetailDto;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.dto.PostListItemDto;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.global.util.TextUtils;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPenaltyService;
import com.webschool.webschool.user.point.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

// 게시글 목록/상세/작성/수정/삭제. 2026-09-28 파일 정리 때 신고(PostReportService)와
// 좋아요·북마크(PostReactionService)를 분리했다 - 댓글 쪽(PostCommentService)도 같은 축으로 나뉘어 있다.
@Service
@RequiredArgsConstructor
public class PostService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_TITLE_LENGTH = 100;
    // 리치 에디터 도입 이후 이 값은 "글자 수"가 아니라 정제된 HTML 문자열 길이 기준이다(본문 중간에
    // 삽입된 이미지/동영상/파일 링크 태그까지 포함) - 순수 텍스트 4000자보다 훨씬 넉넉하게 잡음.
    private static final int MAX_CONTENT_LENGTH = 50000;

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final UserRepository userRepository;
    private final UserPenaltyService userPenaltyService;
    private final UserPointService userPointService;
    private final AdminActionLogService adminActionLogService;
    private final PostImageService postImageService;

    // pageSize: 사용자가 "페이지당 N개 보기"로 고를 수 있는 페이지 크기 (PageUtils.normalizeSize()로
    // 컨트롤러 단에서 이미 5~100 사이로 정규화된 값이 넘어온다).
    // scope: 검색 대상 - "title"(제목만) / "content"(내용만) / 그 외(기본, 제목+내용).
    // sortOption: "oldest"(오래된순) / "views"(조회수 많은순) / "likes"(좋아요순) / 그 외(기본, 최신순).
    public Page<PostListItemDto> getList(int page, Post.Category category, String keyword, int pageSize,
                                          String scope, String sortOption) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, resolveSort(sortOption));
        String normalizedScope = scope == null ? "" : scope;
        Page<Post> result = postRepository.search(category, keyword, normalizedScope, pageable);
        Map<Long, String> thumbnailsByPostId = postImageService.getThumbnailUrls(
                result.getContent().stream().map(Post::getId).collect(Collectors.toList()));
        return result.map(p -> toListItemDto(p, thumbnailsByPostId.get(p.getId())));
    }

    private Sort resolveSort(String sortOption) {
        if ("oldest".equals(sortOption)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        if ("views".equals(sortOption)) {
            return Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        if ("likes".equals(sortOption)) {
            return Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    // 공개 URL(/posts/{uuid})을 내부 PK로 변환 - 컨트롤러가 요청을 받자마자 제일 먼저 호출한다
    public Long resolveIdByUuid(String uuid) {
        return postRepository.findByUuid(uuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND))
                .getId();
    }

    // 댓글 목록 API처럼 게시글 본문이 아니라 "그 글에 딸린 데이터"를 돌려주는 곳에서 쓴다 - 상세
    // 페이지(getDetail)와 똑같은 열람 조건을 통과한 글의 id만 돌려준다. 예전엔 댓글 API가 uuid만으로
    // 조회해서, 상세는 막힌 삭제/블라인드/비공개 글의 댓글을 그대로 읽을 수 있었다(보안 점검 M2).
    @Transactional(readOnly = true)
    public Long resolveReadableIdByUuid(String uuid, String currentUsername) {
        Post post = postRepository.findByUuid(uuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        assertReadable(post, currentUsername);
        return post.getId();
    }

    // 일반 사용자 화면 기준 열람 가능 여부 - 안 되면 "없는 글"(POST_NOT_FOUND)로 응답해 존재 여부도 숨긴다.
    // getDetail/resolveReadableIdByUuid/EmbedResolveController가 같은 기준을 쓰도록 한 곳에 모았다.
    public void assertReadable(Post post, String currentUsername) {
        // 소프트 삭제된 게시물은 일반 사용자 화면에서는 완전히 사라진 것처럼 처리 (작성자 본인도 예외 없음).
        // 관리자가 삭제된 글을 봐야 하면 AdminPostService의 별도 경로를 사용한다.
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        boolean mine = currentUsername != null && post.getAuthor() != null
                && post.getAuthor().getUsername().equals(currentUsername);

        // 블라인드 처리된 게시물은 작성자 본인과 관리자만 열람 가능 (그 외에는 존재하지 않는 것처럼 처리)
        if (post.isBlind() && !mine && !isAdmin(currentUsername)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 공개범위 PRIVATE(비공개) - 링크(uuid)를 알아도 작성자 본인과 관리자 외에는 못 연다.
        // UNLISTED와 갈리는 지점이 정확히 여기다: UNLISTED는 목록/검색에서만 빠지고(PostRepository.
        // search()의 visibility = PUBLIC 조건) 상세는 누구나 열 수 있는 반면, PRIVATE는 상세 진입
        // 자체를 막는다. 블라인드와 동일하게 "없는 글"처럼 처리해서 uuid로 존재 여부를 떠보는 것도
        // 막는다(존재하면 403, 없으면 404처럼 응답이 갈리면 그 자체가 정보 노출이라서).
        if (post.getVisibility() == Post.Visibility.PRIVATE && !mine && !isAdmin(currentUsername)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    @Transactional
    public PostDetailDto getDetail(Long id, String currentUsername, boolean countView) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        assertReadable(post, currentUsername);

        boolean mine = currentUsername != null && post.getAuthor() != null
                && post.getAuthor().getUsername().equals(currentUsername);

        // 원자적 벌크 UPDATE로 조회수 증가(PostRepository.incrementViewCount() 참고) - 엔티티
        // 필드는 건드리지 않고(다른 필드 변경 시 stale 값으로 덮어쓰는 걸 막기 위함, Post.java의
        // @DynamicUpdate 주석 참고), 화면 표시용 값만 로컬 변수로 따로 계산한다.
        int displayViewCount = post.getViewCount();
        if (countView) {
            postRepository.incrementViewCount(id);
            displayViewCount = post.getViewCount() + 1;
        }

        return toDetailDto(post, currentUsername, mine, displayViewCount);
    }

    @Transactional
    public String createPost(String username, PostFormDto form, boolean pollAttached) {
        String title = validateTitle(form.getTitle());
        String content = validateContent(form.getContent(), pollAttached);
        Post.Category category = parseCategory(form.getCategory());

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        userPenaltyService.assertCanCreatePost(author);

        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.setAuthor(author);
        post.setVisibility(parseVisibility(form.getVisibility()));

        Post saved = postRepository.save(post);
        // 감사 로그 커버리지 확장(사용자 요청) - 관리자 조치뿐 아니라 일반 사용자 본인의 주요 활동도
        // 기록한다. log()가 SecurityContextHolder에서 현재 로그인 사용자를 그대로 읽으므로 호출부만
        // 추가하면 된다(시그니처 변경 불필요).
        adminActionLogService.log("POST", saved.getId(), "CREATE", TextUtils.truncate(title) + " [" + category.getLabel() + "]");
        userPointService.award(author, UserPointService.POST_CREATE, "게시글 작성");
        return saved.getUuid();
    }

    public PostFormDto getForEdit(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 게시물만 수정할 수 있습니다.");
        }

        PostFormDto dto = new PostFormDto();
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setCategory(post.getCategory().name());
        dto.setVisibility(post.getVisibility().name());
        return dto;
    }

    @Transactional
    public void updatePost(Long id, String username, PostFormDto form) {
        String title = validateTitle(form.getTitle());
        String content = validateContent(form.getContent(), false);
        Post.Category category = parseCategory(form.getCategory());

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 게시물만 수정할 수 있습니다.");
        }

        Post.Visibility visibility = parseVisibility(form.getVisibility());
        boolean changed = !title.equals(post.getTitle()) || !content.equals(post.getContent())
                || category != post.getCategory() || visibility != post.getVisibility();

        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.setVisibility(visibility);

        if (changed) {
            post.setUpdatedAt(LocalDateTime.now());
            // 내용이 바뀌었으니 예전 "문제없음" 판결은 더 이상 유효하지 않다 - 다시 검토가 필요함
            post.setReportCleared(false);
            adminActionLogService.log("POST", post.getId(), "UPDATE", TextUtils.truncate(title));
        }
    }

    @Transactional
    public void deletePost(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 게시물만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경한다(댓글/신고/이미지는 그대로 보존되고,
        // 관리자 페이지에서 계속 조회 가능하다 - 6-6 항목 참고). FK 제약 문제도 이걸로 근본 해결됨.
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        adminActionLogService.log("POST", post.getId(), "DELETE", TextUtils.truncate(post.getTitle()));
    }

    // **버그 수정**: 예전엔 ROLE_ADMIN(부관리자)만 확인해서 총관리자(ROLE_SUPER_ADMIN)가 블라인드된
    // 글을 일반 커뮤니티 화면(/posts/{uuid})에서 못 보고 관리자 페이지를 거쳐야 하는 문제가 있었다.
    // User.isAdmin()은 두 역할을 모두 포함하므로 그대로 위임한다.
    private boolean isAdmin(String username) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(User::isAdmin)
                .orElse(false);
    }

    // 알림 메시지에 제목을 넣을 때 너무 길어지지 않도록 자르는 용도 (Notification.message는 200자 제한)
    private Post.Category parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return Post.Category.FREE;
        }
        try {
            return Post.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 카테고리입니다.");
        }
    }

    // 공개범위 파싱 - parseCategory()와 달리 잘못된 값에도 예외를 던지지 않고 가장 안전한 쪽
    // (PUBLIC)으로 폴백한다. 카테고리는 폼에 라디오가 항상 하나 선택돼 있어서 값이 비면 조작으로
    // 봐야 하지만, 공개범위는 값이 비어 들어올 수 있는 경로(구버전 캐시된 폼 등)가 있어도
    // "글쓰기가 실패"하는 것보다 기본값으로 저장되는 게 낫다.
    private Post.Visibility parseVisibility(String value) {
        if (value == null || value.isBlank()) {
            return Post.Visibility.PUBLIC;
        }
        try {
            return Post.Visibility.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Post.Visibility.PUBLIC;
        }
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + MAX_TITLE_LENGTH + "자 이내로 입력해주세요.");
        }
        String trimmed = title.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    // 리치 에디터(Quill)가 보낸 HTML을 저장 전에 항상 여기서 정제한다 - th:utext로 그대로 렌더링하므로
    // 이 단계를 거치지 않은 값이 저장되면 안 된다(HtmlSanitizer가 유일한 XSS 방어선).
    // pollAttached: 설문을 함께 첨부하는 글쓰기라면 본문이 비어있어도 통과시킨다 - 설문 자체가
    // 내용 역할을 하므로 "내용을 입력해주세요"로 막을 이유가 없다는 사용자 요청으로 추가.
    private String validateContent(String content, boolean pollAttached) {
        if (content == null || content.isBlank()) {
            if (pollAttached) {
                return "";
            }
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        String sanitized = HtmlSanitizer.sanitize(content.trim());
        String plainText = HtmlSanitizer.toPlainText(sanitized);
        // Quill은 빈 에디터도 "<p><br></p>"처럼 빈 태그를 보낼 수 있어 순수 텍스트 기준으로 판단한다.
        if (plainText.isBlank() && !sanitized.contains("<img") && !sanitized.contains("<video")) {
            if (pollAttached) {
                return sanitized;
            }
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        if (sanitized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("내용은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        BannedWordFilter.validate(plainText);
        return sanitized;
    }

    // author가 null인 경우(하드 삭제, AccountHardDeleteService 참고)도 isDeleted()와 동일하게
    // "탈퇴한 사용자"로 취급한다 - 계정이 진짜로 사라진 것도 소프트 삭제된 것과 화면상 구분할 이유가 없다.
    private String displayNickname(Post p) {
        if (p.getCategory() == Post.Category.ANONYMOUS) {
            return "익명";
        }
        return p.getAuthor() == null || p.getAuthor().isDeleted() ? "탈퇴한 사용자" : p.getAuthor().getNickname();
    }

    private PostListItemDto toListItemDto(Post p, String thumbnailUrl) {
        return PostListItemDto.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .title(p.getTitle())
                .nickname(displayNickname(p))
                .authorId(p.getAuthor() != null ? p.getAuthor().getId() : null)
                .authorUuid(p.getAuthor() != null ? p.getAuthor().getUuid() : null)
                .authorLinkable(isAuthorLinkable(p))
                .category(p.getCategory().name())
                .categoryLabel(p.getCategory().getLabel())
                .thumbnailUrl(thumbnailUrl)
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(p.getViewCount())
                .likeCount(p.getLikeCount())
                .build();
    }

    // 익명 게시물이면 프로필로 연결하면 안 되고(작성자가 누구인지 드러남), 작성자가 탈퇴했으면
    // 애초에 볼 수 있는 프로필이 없다(UserProfileService.getProfile()이 탈퇴 계정을 막음).
    private boolean isAuthorLinkable(Post p) {
        return p.getCategory() != Post.Category.ANONYMOUS && p.getAuthor() != null && !p.getAuthor().isDeleted();
    }

    private PostDetailDto toDetailDto(Post p, String currentUsername, boolean mine, int displayViewCount) {
        boolean reportedByMe = !mine && currentUsername != null
                && postReportRepository.existsByPost_IdAndReporter_Username(p.getId(), currentUsername);
        boolean likedByMe = currentUsername != null
                && postLikeRepository.existsByPost_IdAndUser_Username(p.getId(), currentUsername);
        boolean bookmarkedByMe = currentUsername != null
                && postBookmarkRepository.existsByPost_IdAndUser_Username(p.getId(), currentUsername);

        return PostDetailDto.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .title(p.getTitle())
                .content(p.getContent())
                .nickname(displayNickname(p))
                .authorId(p.getAuthor() != null ? p.getAuthor().getId() : null)
                .authorUuid(p.getAuthor() != null ? p.getAuthor().getUuid() : null)
                .authorLinkable(isAuthorLinkable(p))
                .category(p.getCategory().name())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(displayViewCount)
                .reportCount(p.getReportCount())
                .likeCount(p.getLikeCount())
                .likedByMe(likedByMe)
                .bookmarkedByMe(bookmarkedByMe)
                .blind(p.isBlind())
                .edited(p.getUpdatedAt() != null)
                .mine(mine)
                .reportedByMe(reportedByMe)
                .visibility(p.getVisibility().name())
                .visibilityLabel(p.getVisibility().getLabel())
                .visibilityDescription(p.getVisibility().getDescription())
                .build();
    }
}
