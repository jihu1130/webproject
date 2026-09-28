package com.webschool.webschool.school.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.domain.ScheduleCommentBookmark;
import com.webschool.webschool.school.domain.ScheduleCommentLike;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// 오늘의 한마디 좋아요/북마크 - PostReactionService/CommentReactionService와 동일한 패턴.
// 2026-09-28 ScheduleCommentService에서 분리.
@Service
@RequiredArgsConstructor
public class ScheduleCommentReactionService {

    private final ScheduleCommentRepository scheduleCommentRepository;
    private final ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    private final ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public Map<String, Object> toggleLike(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var existing = scheduleCommentLikeRepository.findByComment_IdAndUser_Id(id, user.getId());
        boolean liked;
        int displayLikeCount;
        if (existing.isPresent()) {
            scheduleCommentLikeRepository.delete(existing.get());
            scheduleCommentRepository.decrementLikeCount(id);
            displayLikeCount = Math.max(0, comment.getLikeCount() - 1);
            liked = false;
        } else {
            ScheduleCommentLike like = new ScheduleCommentLike();
            like.setComment(comment);
            like.setUser(user);
            scheduleCommentLikeRepository.save(like);
            scheduleCommentRepository.incrementLikeCount(id);
            displayLikeCount = comment.getLikeCount() + 1;
            liked = true;
            notificationService.notifyIfNotSelf(comment.getUser(), username, Notification.Type.LIKE,
                    user.getNickname() + "님이 회원님의 오늘의 한마디를 좋아합니다.",
                    "/school/comments/" + comment.getUuid());
        }
        return Map.of("liked", liked, "likeCount", displayLikeCount);
    }

    @Transactional
    public boolean toggleBookmark(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var existing = scheduleCommentBookmarkRepository.findByComment_IdAndUser_Id(id, user.getId());
        if (existing.isPresent()) {
            scheduleCommentBookmarkRepository.delete(existing.get());
            return false;
        }
        ScheduleCommentBookmark bookmark = new ScheduleCommentBookmark();
        bookmark.setComment(comment);
        bookmark.setUser(user);
        scheduleCommentBookmarkRepository.save(bookmark);
        return true;
    }

    // 마이페이지 "북마크" 탭(한마디)의 "해제" 버튼 전용 - PostReactionService.removeBookmark()와 동일한 이유로
    // 토글이 아닌 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeBookmark(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        scheduleCommentBookmarkRepository.findByComment_IdAndUser_Id(id, user.getId())
                .ifPresent(scheduleCommentBookmarkRepository::delete);
    }

    // 마이페이지 "좋아요" 탭(한마디)의 "취소" 버튼 전용 - PostReactionService.removeLike()와 동일한 이유로
    // 토글이 아닌 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeLike(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        scheduleCommentLikeRepository.findByComment_IdAndUser_Id(id, user.getId()).ifPresent(like -> {
            scheduleCommentLikeRepository.delete(like);
            scheduleCommentRepository.decrementLikeCount(id);
        });
    }
}
