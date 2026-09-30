package com.webschool.webschool.post.comment.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.comment.domain.CommentBookmark;
import com.webschool.webschool.post.comment.domain.CommentLike;
import com.webschool.webschool.post.comment.domain.PostComment;
import com.webschool.webschool.post.comment.repository.CommentBookmarkRepository;
import com.webschool.webschool.post.comment.repository.CommentLikeRepository;
import com.webschool.webschool.post.comment.repository.PostCommentRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.point.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// 게시글 댓글 좋아요/북마크 - PostReactionService와 동일한 패턴. 2026-09-28 PostCommentService에서 분리.
@Service
@RequiredArgsConstructor
public class CommentReactionService {

    private final PostCommentRepository postCommentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentBookmarkRepository commentBookmarkRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserPointService userPointService;

    @Transactional
    public Map<String, Object> toggleLike(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
            userPointService.award(comment.getAuthor(), UserPointService.LIKE_RECEIVED, "댓글 좋아요 받음");
        }
        return Map.of("liked", liked, "likeCount", displayLikeCount);
    }

    @Transactional
    public boolean toggleBookmark(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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

    // 마이페이지 "북마크" 탭(댓글 서브탭)의 "해제" 버튼 전용 - PostReactionService.removeBookmark()와
    // 동일한 이유로 토글이 아닌 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeBookmark(Long commentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        commentBookmarkRepository.findByComment_IdAndUser_Id(commentId, user.getId())
                .ifPresent(commentBookmarkRepository::delete);
    }

    // 마이페이지 "좋아요" 탭(댓글 서브탭)의 "취소" 버튼 전용 - PostReactionService.removeLike()와 동일한 패턴.
    @Transactional
    public void removeLike(Long commentId, String username) {
        postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        commentLikeRepository.findByComment_IdAndUser_Id(commentId, user.getId()).ifPresent(like -> {
            commentLikeRepository.delete(like);
            postCommentRepository.decrementLikeCount(commentId);
        });
    }
}
