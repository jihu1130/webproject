package com.webschool.webschool.post.service;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostBookmark;
import com.webschool.webschool.post.domain.PostLike;
import com.webschool.webschool.post.domain.PostReport;
import com.webschool.webschool.post.dto.PostDetailDto;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.dto.PostListItemDto;
import com.webschool.webschool.post.dto.PostReportResultDto;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPenaltyService;
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

@Service
@RequiredArgsConstructor
public class PostService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_TITLE_LENGTH = 100;
    // 리치 에디터 도입 이후 이 값은 "글자 수"가 아니라 정제된 HTML 문자열 길이 기준이다(본문 중간에
    // 삽입된 이미지/동영상/파일 링크 태그까지 포함) - 순수 텍스트 4000자보다 훨씬 넉넉하게 잡음.
    private static final int MAX_CONTENT_LENGTH = 50000;
    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserPenaltyService userPenaltyService;

    // pageSize: 사용자가 "페이지당 N개 보기"로 고를 수 있는 페이지 크기 (PageUtils.normalizeSize()로
    // 컨트롤러 단에서 이미 5~100 사이로 정규화된 값이 넘어온다).
    // scope: 검색 대상 - "title"(제목만) / "content"(내용만) / 그 외(기본, 제목+내용).
    // sortOption: "oldest"(오래된순) / "views"(조회수 많은순) / 그 외(기본, 최신순).
    public Page<PostListItemDto> getList(int page, Post.Category category, String keyword, int pageSize,
                                          String scope, String sortOption) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, resolveSort(sortOption));
        String normalizedScope = scope == null ? "" : scope;
        Page<Post> result = postRepository.search(category, keyword, normalizedScope, pageable);
        return result.map(this::toListItemDto);
    }

    private Sort resolveSort(String sortOption) {
        if ("oldest".equals(sortOption)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        if ("views".equals(sortOption)) {
            return Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    // 공개 URL(/posts/{uuid})을 내부 PK로 변환 - 컨트롤러가 요청을 받자마자 제일 먼저 호출한다
    public Long resolveIdByUuid(String uuid) {
        return postRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."))
                .getId();
    }

    @Transactional
    public PostDetailDto getDetail(Long id, String currentUsername, boolean countView) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        // 소프트 삭제된 게시물은 일반 사용자 화면에서는 완전히 사라진 것처럼 처리 (작성자 본인도 예외 없음).
        // 관리자가 삭제된 글을 봐야 하면 AdminPostService의 별도 경로를 사용한다.
        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        boolean mine = currentUsername != null && post.getAuthor().getUsername().equals(currentUsername);

        // 블라인드 처리된 게시물은 작성자 본인과 관리자만 열람 가능 (그 외에는 존재하지 않는 것처럼 처리)
        if (post.isBlind() && !mine && !isAdmin(currentUsername)) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (countView) {
            post.setViewCount(post.getViewCount() + 1);
        }

        return toDetailDto(post, currentUsername, mine);
    }

    @Transactional
    public String createPost(String username, PostFormDto form) {
        String title = validateTitle(form.getTitle());
        String content = validateContent(form.getContent());
        Post.Category category = parseCategory(form.getCategory());

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        userPenaltyService.assertCanCreatePost(author);

        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.setAuthor(author);

        return postRepository.save(post).getUuid();
    }

    public PostFormDto getForEdit(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 수정할 수 있습니다.");
        }

        PostFormDto dto = new PostFormDto();
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setCategory(post.getCategory().name());
        return dto;
    }

    @Transactional
    public void updatePost(Long id, String username, PostFormDto form) {
        String title = validateTitle(form.getTitle());
        String content = validateContent(form.getContent());
        Post.Category category = parseCategory(form.getCategory());

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 수정할 수 있습니다.");
        }

        boolean changed = !title.equals(post.getTitle()) || !content.equals(post.getContent())
                || category != post.getCategory();

        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);

        if (changed) {
            post.setUpdatedAt(LocalDateTime.now());
            // 내용이 바뀌었으니 예전 "문제없음" 판결은 더 이상 유효하지 않다 - 다시 검토가 필요함
            post.setReportCleared(false);
        }
    }

    @Transactional
    public void deletePost(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경한다(댓글/신고/이미지는 그대로 보존되고,
        // 관리자 페이지에서 계속 조회 가능하다 - 6-6 항목 참고). FK 제약 문제도 이걸로 근본 해결됨.
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public PostReportResultDto reportPost(Long id, String username, String reason) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isReportCleared()) {
            throw new IllegalArgumentException("이미 검토되어 문제없다고 판정된 게시물입니다.");
        }

        if (post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물은 신고할 수 없습니다.");
        }

        if (postReportRepository.existsByPost_IdAndReporter_Username(id, username)) {
            throw new IllegalArgumentException("이미 신고한 게시물입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }

        PostReport report = new PostReport();
        report.setPost(post);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        postReportRepository.save(report);

        boolean wasBlind = post.isBlind();
        post.setReportCount(post.getReportCount() + 1);
        if (!wasBlind && post.getReportCount() >= BLIND_THRESHOLD) {
            post.setBlind(true);
            notificationService.notify(post.getAuthor(), Notification.Type.REPORT_ACTION,
                    "'" + truncate(post.getTitle()) + "' 글이 신고 누적으로 블라인드 처리되었습니다.",
                    "/posts/" + post.getUuid());
        }

        return new PostReportResultDto(post.getReportCount(), post.isBlind());
    }

    // 좋아요 토글 - 이미 눌렀으면 취소, 안 눌렀으면 추가. PostLike 유니크 제약(post_id, user_id)
    // 덕분에 같은 사람이 두 번 좋아요를 쌓을 수 없다. 카운트는 Post.likeCount에 비정규화해서
    // 목록/상세 조회 때마다 COUNT 쿼리를 따로 안 날려도 되게 한다.
    @Transactional
    public Map<String, Object> toggleLike(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        var existing = postLikeRepository.findByPost_IdAndUser_Id(id, user.getId());
        boolean liked;
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            liked = false;
        } else {
            PostLike like = new PostLike();
            like.setPost(post);
            like.setUser(user);
            postLikeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            liked = true;
            notificationService.notifyIfNotSelf(post.getAuthor(), username, Notification.Type.LIKE,
                    user.getNickname() + "님이 회원님의 글 '" + truncate(post.getTitle()) + "'을(를) 좋아합니다.",
                    "/posts/" + post.getUuid());
        }
        return Map.of("liked", liked, "likeCount", post.getLikeCount());
    }

    // 북마크 토글 - 좋아요와 동일한 패턴이지만 카운트를 공개하지 않는 개인용 기능이라 PostBookmark
    // 자체가 마이페이지 "북마크" 탭의 조회 근거가 된다(비정규화 카운트 없음).
    @Transactional
    public boolean toggleBookmark(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        var existing = postBookmarkRepository.findByPost_IdAndUser_Id(id, user.getId());
        if (existing.isPresent()) {
            postBookmarkRepository.delete(existing.get());
            return false;
        }
        PostBookmark bookmark = new PostBookmark();
        bookmark.setPost(post);
        bookmark.setUser(user);
        postBookmarkRepository.save(bookmark);
        return true;
    }

    // 마이페이지 "북마크" 탭의 "해제" 버튼 전용 - toggleBookmark()와 달리 항상 "제거"만 하는
    // 멱등 동작이다(토글을 재사용하면 이미 해제된 상태에서 다시 누를 때 오히려 북마크가 켜지는
    // 사고가 날 수 있어 분리함).
    @Transactional
    public void removeBookmark(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        postBookmarkRepository.findByPost_IdAndUser_Id(id, user.getId())
                .ifPresent(postBookmarkRepository::delete);
    }

    // 마이페이지 "좋아요" 탭의 "취소" 버튼 전용 - removeBookmark()와 동일한 이유로 토글이 아닌
    // 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeLike(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        postLikeRepository.findByPost_IdAndUser_Id(id, user.getId()).ifPresent(like -> {
            postLikeRepository.delete(like);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        });
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
    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

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
    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        String sanitized = HtmlSanitizer.sanitize(content.trim());
        String plainText = HtmlSanitizer.toPlainText(sanitized);
        // Quill은 빈 에디터도 "<p><br></p>"처럼 빈 태그를 보낼 수 있어 순수 텍스트 기준으로 판단한다.
        if (plainText.isBlank() && !sanitized.contains("<img") && !sanitized.contains("<video")) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        if (sanitized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("내용은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        BannedWordFilter.validate(plainText);
        return sanitized;
    }

    private String displayNickname(Post p) {
        if (p.getCategory() == Post.Category.ANONYMOUS) {
            return "익명";
        }
        return p.getAuthor().isDeleted() ? "탈퇴한 사용자" : p.getAuthor().getNickname();
    }

    private PostListItemDto toListItemDto(Post p) {
        return PostListItemDto.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .title(p.getTitle())
                .nickname(displayNickname(p))
                .authorId(p.getAuthor().getId())
                .authorLinkable(isAuthorLinkable(p))
                .category(p.getCategory().name())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(p.getViewCount())
                .likeCount(p.getLikeCount())
                .build();
    }

    // 익명 게시물이면 프로필로 연결하면 안 되고(작성자가 누구인지 드러남), 작성자가 탈퇴했으면
    // 애초에 볼 수 있는 프로필이 없다(UserProfileService.getProfile()이 탈퇴 계정을 막음).
    private boolean isAuthorLinkable(Post p) {
        return p.getCategory() != Post.Category.ANONYMOUS && !p.getAuthor().isDeleted();
    }

    private PostDetailDto toDetailDto(Post p, String currentUsername, boolean mine) {
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
                .authorId(p.getAuthor().getId())
                .authorLinkable(isAuthorLinkable(p))
                .category(p.getCategory().name())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(p.getViewCount())
                .reportCount(p.getReportCount())
                .likeCount(p.getLikeCount())
                .likedByMe(likedByMe)
                .bookmarkedByMe(bookmarkedByMe)
                .blind(p.isBlind())
                .edited(p.getUpdatedAt() != null)
                .mine(mine)
                .reportedByMe(reportedByMe)
                .build();
    }
}
