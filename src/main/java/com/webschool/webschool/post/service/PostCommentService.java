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
import java.util.Collections;
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

    // 버그 수정(N+1) - 댓글마다 신고/좋아요/북마크 여부를 개별 existsBy 쿼리로 확인하던 것을, 이 글의
    // 댓글 id 목록 전체에 대해 한 번씩만(총 3번) 배치 조회해서 메모리에서 lookup하도록 교체했다
    // (댓글 C개짜리 글이면 예전엔 최대 1 + 3C번 쿼리, 이제는 항상 4번 - 작성자 조회는 findBy...의
    // @EntityGraph로 이미 JOIN FETCH돼 있어서 별도).
    public List<PostCommentDto> getComments(Long postId, String currentUsername) {
        List<PostComment> comments = postCommentRepository.findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(postId);

        Set<Long> reportedIds = Collections.emptySet();
        Set<Long> likedIds = Collections.emptySet();
        Set<Long> bookmarkedIds = Collections.emptySet();
        if (currentUsername != null && !comments.isEmpty()) {
            List<Long> commentIds = comments.stream().map(PostComment::getId).collect(Collectors.toList());
            reportedIds = Set.copyOf(commentReportRepository.findReportedCommentIds(commentIds, currentUsername));
            likedIds = Set.copyOf(commentLikeRepository.findLikedCommentIds(commentIds, currentUsername));
            bookmarkedIds = Set.copyOf(commentBookmarkRepository.findBookmarkedCommentIds(commentIds, currentUsername));
        }

        // QNA 채택 답변(네이버 지식인 스타일)이 있으면 항상 맨 위로 - Stream.sorted()는 안정 정렬이라
        // 채택 여부가 같은 댓글끼리는 원래의 작성일순(오름차순)이 그대로 유지된다.
        Set<Long> finalReportedIds = reportedIds;
        Set<Long> finalLikedIds = likedIds;
        Set<Long> finalBookmarkedIds = bookmarkedIds;
        return comments.stream()
                .map(c -> toDto(c, currentUsername, finalReportedIds, finalLikedIds, finalBookmarkedIds))
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
        postCommentRepository.incrementReportCount(commentId);
        int displayReportCount = comment.getReportCount() + 1;
        boolean nowBlind = wasBlind;
        if (!wasBlind && displayReportCount >= BLIND_THRESHOLD) {
            comment.setBlind(true);
            nowBlind = true;
            notificationService.notify(comment.getAuthor(), Notification.Type.REPORT_ACTION,
                    "작성하신 댓글이 신고 누적으로 블라인드 처리되었습니다.",
                    "/posts/" + comment.getPost().getUuid());
        }

        return new CommentReportResultDto(displayReportCount, nowBlind);
    }

    // 신고 취소 - PostService.cancelReport()와 동일한 이유/패턴(자동 언블라인드는 하지 않음).
    @Transactional
    public void cancelReport(Long commentId, String username) {
        commentReportRepository.findByComment_IdAndReporter_Username(commentId, username).ifPresent(report -> {
            commentReportRepository.delete(report);
            postCommentRepository.decrementReportCount(commentId);
        });
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
        int displayLikeCount;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            postCommentRepository.decrementLikeCount(commentId);
            displayLikeCount = Math.max(0, comment.getLikeCount() - 1);
            liked = false;
        } else {
            CommentLike like = new CommentLike();
            like.setComment(comment);
            like.setUser(user);
            commentLikeRepository.save(like);
            postCommentRepository.incrementLikeCount(commentId);
            displayLikeCount = comment.getLikeCount() + 1;
            liked = true;
            notificationService.notifyIfNotSelf(comment.getAuthor(), username, Notification.Type.LIKE,
                    user.getNickname() + "님이 회원님의 댓글을 좋아합니다.",
                    "/posts/" + comment.getPost().getUuid());
        }
        return Map.of("liked", liked, "likeCount", displayLikeCount);
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
        // 수정사항.md 지적 - 질문자가 자기 자신의 댓글을 채택할 수 있어서 "다른 사람이 도와준
        // 답을 표시"한다는 채택 기능의 취지가 무의미해졌다.
        if (comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 답변은 채택할 수 없습니다.");
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

    // 댓글 하나만 변환할 때(작성/수정 직후 응답용) - 이 경로는 애초에 댓글 하나뿐이라 개별 existsBy
    // 쿼리 3번이 N+1 문제가 되지 않는다. 목록 전체를 변환할 때는 아래 배치 버전(getComments() 참고)을 쓴다.
    private PostCommentDto toDto(PostComment c, String currentUsername) {
        boolean mine = currentUsername != null && c.getAuthor().getUsername().equals(currentUsername);
        boolean reportedByMe = !mine && currentUsername != null
                && commentReportRepository.existsByComment_IdAndReporter_Username(c.getId(), currentUsername);
        boolean likedByMe = currentUsername != null
                && commentLikeRepository.existsByComment_IdAndUser_Username(c.getId(), currentUsername);
        boolean bookmarkedByMe = currentUsername != null
                && commentBookmarkRepository.existsByComment_IdAndUser_Username(c.getId(), currentUsername);
        return buildDto(c, currentUsername, mine, reportedByMe, likedByMe, bookmarkedByMe);
    }

    // getComments()가 미리 배치 조회해 둔 신고/좋아요/북마크 id 집합으로 멤버십만 확인 - 댓글마다
    // 개별 쿼리를 날리지 않는다(버그 수정: N+1).
    private PostCommentDto toDto(PostComment c, String currentUsername,
                                  Set<Long> reportedIds, Set<Long> likedIds, Set<Long> bookmarkedIds) {
        boolean mine = currentUsername != null && c.getAuthor().getUsername().equals(currentUsername);
        boolean reportedByMe = !mine && reportedIds.contains(c.getId());
        boolean likedByMe = likedIds.contains(c.getId());
        boolean bookmarkedByMe = bookmarkedIds.contains(c.getId());
        return buildDto(c, currentUsername, mine, reportedByMe, likedByMe, bookmarkedByMe);
    }

    private PostCommentDto buildDto(PostComment c, String currentUsername, boolean mine,
                                     boolean reportedByMe, boolean likedByMe, boolean bookmarkedByMe) {
        // 블라인드된 댓글은 작성자 본인/관리자에게만 원본 내용을 보여준다 (PostService의 블라인드 가시성 판단과 동일 패턴)
        String content = c.isBlind() && !mine && !isAdmin(currentUsername) ? BLIND_PLACEHOLDER : c.getContent();

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
