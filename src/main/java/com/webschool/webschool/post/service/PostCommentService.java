package com.webschool.webschool.post.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.CommentBookmark;
import com.webschool.webschool.post.domain.CommentLike;
import com.webschool.webschool.post.domain.CommentReport;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostComment;
import com.webschool.webschool.post.dto.CommentReportResultDto;
import com.webschool.webschool.post.dto.PostCommentDto;
import com.webschool.webschool.post.repository.CommentBookmarkRepository;
import com.webschool.webschool.post.repository.CommentLikeRepository;
import com.webschool.webschool.post.repository.CommentReportRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserBlockService;
import com.webschool.webschool.user.service.UserPenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostCommentService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드 (PostService와 동일)
    private static final String BLIND_PLACEHOLDER = "신고 누적으로 블라인드 처리된 댓글입니다.";

    private final PostCommentRepository postCommentRepository;
    private final CommentReportRepository commentReportRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentBookmarkRepository commentBookmarkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserPenaltyService userPenaltyService;
    private final UserBlockService userBlockService;

    public List<PostCommentDto> getComments(Long postId, String currentUsername) {
        // findByPost_IdAndDeletedFalseOrderByCreatedAtAsc()에 @EntityGraph(author)가 붙어있어 작성자는
        // 이미 한 번에 같이 조회된다.
        List<PostComment> comments = postCommentRepository.findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(postId);

        // 버그 수정(N+1) - 예전엔 댓글마다 신고/좋아요/북마크 여부를 existsBy...로 따로 조회해서
        // 댓글 C개짜리 글 하나를 열람할 때마다 최대 1 + 4C번의 SQL이 실행됐다(작성자 조회 + 신고/
        // 좋아요/북마크 여부 각각). 댓글 목록 전체에 대해 딱 3번의 배치 쿼리로 바꾼다.
        Set<Long> reportedIds;
        Set<Long> likedIds;
        Set<Long> bookmarkedIds;
        if (currentUsername == null || comments.isEmpty()) {
            reportedIds = Set.of();
            likedIds = Set.of();
            bookmarkedIds = Set.of();
        } else {
            List<Long> commentIds = comments.stream().map(PostComment::getId).collect(Collectors.toList());
            reportedIds = Set.copyOf(commentReportRepository.findReportedCommentIds(commentIds, currentUsername));
            likedIds = Set.copyOf(commentLikeRepository.findLikedCommentIds(commentIds, currentUsername));
            bookmarkedIds = Set.copyOf(commentBookmarkRepository.findBookmarkedCommentIds(commentIds, currentUsername));
        }

        // QNA 채택 답변(네이버 지식인 스타일)이 있으면 항상 맨 위로 - Stream.sorted()는 안정 정렬이라
        // 채택 여부가 같은 댓글끼리는 원래의 작성일순(오름차순)이 그대로 유지된다.
        return comments.stream()
                .map(c -> toDto(c, currentUsername, reportedIds, likedIds, bookmarkedIds))
                .sorted(Comparator.comparing(PostCommentDto::isAccepted).reversed())
                .collect(Collectors.toList());
    }

    @Transactional
    public PostCommentDto createComment(Long postId, String username, String content) {
        String trimmed = validateContent(content);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        userPenaltyService.assertCanComment(author);

        // 차단은 익명 게시물에는 적용하지 않는다(작성자 식별 자체가 가려져 있어서 차단이라는
        // 개념이 성립하지 않음 - UserBlockService 클래스 주석 참고)
        if (post.getCategory() != Post.Category.ANONYMOUS) {
            userBlockService.assertNotBlocked(author, post.getAuthor());
        }

        PostComment comment = new PostComment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(trimmed);

        postCommentRepository.save(comment);

        String label = post.getCategory() == Post.Category.ANONYMOUS ? "답변" : "댓글";
        notificationService.notifyIfNotSelf(post.getAuthor(), username, Notification.Type.COMMENT,
                author.getNickname() + "님이 회원님의 글에 " + label + "을 남겼어요: " + truncate(post.getTitle()),
                "/posts/" + post.getUuid());

        return toDto(comment, username);
    }

    @Transactional
    public PostCommentDto updateComment(Long commentId, String username, String content) {
        String trimmed = validateContent(content);

        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }

        if (!trimmed.equals(comment.getContent())) {
            comment.setContent(trimmed);
            comment.setUpdatedAt(LocalDateTime.now());
            // 내용이 바뀌었으니 예전 "문제없음" 판결은 더 이상 유효하지 않다 - 다시 검토가 필요함
            comment.setReportCleared(false);
        }

        return toDto(comment, username);
    }

    @Transactional
    public void deleteComment(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경 (관리자 페이지에서 계속 조회 가능, 6-6 항목 참고)
        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public CommentReportResultDto reportComment(Long commentId, String username, String reason) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (comment.isReportCleared()) {
            throw new IllegalArgumentException("이미 검토되어 문제없다고 판정된 댓글입니다.");
        }

        if (comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글은 신고할 수 없습니다.");
        }

        if (commentReportRepository.existsByComment_IdAndReporter_Username(commentId, username)) {
            throw new IllegalArgumentException("이미 신고한 댓글입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }

        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        commentReportRepository.save(report);

        boolean wasBlind = comment.isBlind();
        // 버그 수정: 원자적 벌크 UPDATE로 교체(lost update 방지, PostCommentRepository.incrementReportCount() 참고)
        postCommentRepository.incrementReportCount(commentId);
        int newReportCount = comment.getReportCount() + 1;
        if (!wasBlind && newReportCount >= BLIND_THRESHOLD) {
            comment.setBlind(true);
            notificationService.notify(comment.getAuthor(), Notification.Type.REPORT_ACTION,
                    "작성하신 댓글이 신고 누적으로 블라인드 처리되었습니다.",
                    "/posts/" + comment.getPost().getUuid());
        }

        return new CommentReportResultDto(newReportCount, comment.isBlind());
    }

    // PostService.toggleLike()/toggleBookmark()와 동일한 패턴
    @Transactional
    public Map<String, Object> toggleLike(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        var existing = commentLikeRepository.findByComment_IdAndUser_Id(commentId, user.getId());
        boolean liked;
        int newLikeCount;
        // 버그 수정: 원자적 벌크 UPDATE로 교체(lost update 방지, PostCommentRepository.incrementLikeCount() 참고)
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            postCommentRepository.decrementLikeCount(commentId);
            newLikeCount = Math.max(0, comment.getLikeCount() - 1);
            liked = false;
        } else {
            CommentLike like = new CommentLike();
            like.setComment(comment);
            like.setUser(user);
            commentLikeRepository.save(like);
            postCommentRepository.incrementLikeCount(commentId);
            newLikeCount = comment.getLikeCount() + 1;
            liked = true;
            notificationService.notifyIfNotSelf(comment.getAuthor(), username, Notification.Type.LIKE,
                    user.getNickname() + "님이 회원님의 댓글을 좋아합니다.",
                    "/posts/" + comment.getPost().getUuid());
        }
        return Map.of("liked", liked, "likeCount", newLikeCount);
    }

    @Transactional
    public boolean toggleBookmark(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        var existing = commentBookmarkRepository.findByComment_IdAndUser_Id(commentId, user.getId());
        if (existing.isPresent()) {
            commentBookmarkRepository.delete(existing.get());
            return false;
        }
        CommentBookmark bookmark = new CommentBookmark();
        bookmark.setComment(comment);
        bookmark.setUser(user);
        commentBookmarkRepository.save(bookmark);
        return true;
    }

    // QNA 답변 채택(네이버 지식인 스타일, 2026-08-19 추가) - 질문 작성자만 채택할 수 있고, 새로
    // 채택하면 기존에 채택돼 있던 답변은 자동으로 해제된다(공지사항 "활성 공지 항상 1개" 패턴과
    // 동일하게 "채택 항상 최대 1개"). 이미 채택된 답변을 다시 누르면 채택이 취소된다(토글).
    @Transactional
    public boolean acceptAnswer(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        Post post = comment.getPost();
        if (post.getCategory() != Post.Category.QNA) {
            throw new IllegalArgumentException("질의응답 게시글에서만 답변을 채택할 수 있습니다.");
        }
        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("질문 작성자만 답변을 채택할 수 있습니다.");
        }

        if (comment.isAccepted()) {
            comment.setAccepted(false);
            return false;
        }

        postCommentRepository.findByPost_IdAndAcceptedTrue(post.getId())
                .ifPresent(prev -> prev.setAccepted(false));
        comment.setAccepted(true);
        notificationService.notifyIfNotSelf(comment.getAuthor(), username, Notification.Type.ACCEPTED,
                "회원님의 답변이 채택됐어요: " + truncate(post.getTitle()),
                "/posts/" + post.getUuid());
        return true;
    }

    // 마이페이지 "북마크" 탭(댓글 서브탭)의 "해제" 버튼 전용 - PostService.removeBookmark()와 동일한
    // 이유로 토글이 아닌 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeBookmark(Long commentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        commentBookmarkRepository.findByComment_IdAndUser_Id(commentId, user.getId())
                .ifPresent(commentBookmarkRepository::delete);
    }

    // 마이페이지 "좋아요" 탭(댓글 서브탭)의 "취소" 버튼 전용 - PostService.removeLike()와 동일한 패턴.
    @Transactional
    public void removeLike(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        commentLikeRepository.findByComment_IdAndUser_Id(commentId, user.getId()).ifPresent(like -> {
            commentLikeRepository.delete(like);
            postCommentRepository.decrementLikeCount(commentId);
        });
    }

    // PostService.isAdmin()과 동일한 버그 수정 - 총관리자도 블라인드된 댓글 원본을 볼 수 있어야 한다
    private boolean isAdmin(String username) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(User::isAdmin)
                .orElse(false);
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        String trimmed = content.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    // createComment()/updateComment()처럼 댓글 하나만 다루는 곳에서 쓰는 버전 - existsBy...로 그때그때
    // 확인해도 N+1 문제가 없다(댓글이 1개뿐이므로).
    private PostCommentDto toDto(PostComment c, String currentUsername) {
        boolean reportedByMe = currentUsername != null
                && commentReportRepository.existsByComment_IdAndReporter_Username(c.getId(), currentUsername);
        boolean likedByMe = currentUsername != null
                && commentLikeRepository.existsByComment_IdAndUser_Username(c.getId(), currentUsername);
        boolean bookmarkedByMe = currentUsername != null
                && commentBookmarkRepository.existsByComment_IdAndUser_Username(c.getId(), currentUsername);
        return toDto(c, currentUsername, reportedByMe, likedByMe, bookmarkedByMe);
    }

    // getComments()가 배치로 미리 조회해둔 Set을 넘겨받는 버전 - N+1 방지(getComments() 주석 참고).
    private PostCommentDto toDto(PostComment c, String currentUsername,
                                  Set<Long> reportedIds, Set<Long> likedIds, Set<Long> bookmarkedIds) {
        return toDto(c, currentUsername, reportedIds.contains(c.getId()),
                likedIds.contains(c.getId()), bookmarkedIds.contains(c.getId()));
    }

    private PostCommentDto toDto(PostComment c, String currentUsername,
                                  boolean reportedByMeRaw, boolean likedByMe, boolean bookmarkedByMe) {
        boolean mine = currentUsername != null && c.getAuthor().getUsername().equals(currentUsername);
        // 블라인드된 댓글은 작성자 본인/관리자에게만 원본 내용을 보여준다 (PostService의 블라인드 가시성 판단과 동일 패턴)
        String content = c.isBlind() && !mine && !isAdmin(currentUsername) ? BLIND_PLACEHOLDER : c.getContent();
        boolean reportedByMe = !mine && reportedByMeRaw;

        return PostCommentDto.builder()
                .id(c.getId())
                .nickname(c.getAuthor().isDeleted() ? "탈퇴한 사용자" : c.getAuthor().getNickname())
                .authorId(c.getAuthor().getId())
                .authorLinkable(!c.getAuthor().isDeleted())
                .content(content)
                .createdAt(c.getCreatedAt().format(DISPLAY_FORMAT))
                .edited(c.getUpdatedAt() != null)
                .mine(mine)
                .blind(c.isBlind())
                .reportedByMe(reportedByMe)
                .likeCount(c.getLikeCount())
                .likedByMe(likedByMe)
                .bookmarkedByMe(bookmarkedByMe)
                .accepted(c.isAccepted())
                .build();
    }
}
