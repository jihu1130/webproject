package com.webschool.webschool.post.comment.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.global.util.TextUtils;
import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.comment.domain.CommentReport;
import com.webschool.webschool.post.comment.domain.PostComment;
import com.webschool.webschool.post.comment.dto.CommentReportResultDto;
import com.webschool.webschool.post.comment.repository.CommentReportRepository;
import com.webschool.webschool.post.comment.repository.PostCommentRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 게시글 댓글 신고 → 자동 블라인드(PostReportService와 같은 패턴). 2026-09-28 PostCommentService에서 분리.
@Service
@RequiredArgsConstructor
public class CommentReportService {

    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드 (게시글과 동일)

    private final PostCommentRepository postCommentRepository;
    private final CommentReportRepository commentReportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminActionLogService adminActionLogService;

    @Transactional
    public CommentReportResultDto reportComment(Long commentId, String username, String reason) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (comment.isReportCleared()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 검토되어 문제없다고 판정된 댓글입니다.");
        }

        if (comment.getAuthor() != null && comment.getAuthor().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 댓글은 신고할 수 없습니다.");
        }

        if (commentReportRepository.existsByComment_IdAndReporter_Username(commentId, username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신고한 댓글입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }
        BannedWordFilter.validate(trimmedReason);

        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        commentReportRepository.save(report);
        adminActionLogService.log("COMMENT", commentId, "REPORT", trimmedReason != null ? TextUtils.truncate(trimmedReason) : TextUtils.truncate(comment.getContent()));

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

    // 신고 취소 - PostReportService.cancelReport()와 동일한 이유/패턴(자동 언블라인드는 하지 않음).
    @Transactional
    public void cancelReport(Long commentId, String username) {
        commentReportRepository.findByComment_IdAndReporter_Username(commentId, username).ifPresent(report -> {
            commentReportRepository.delete(report);
            postCommentRepository.decrementReportCount(commentId);
            adminActionLogService.log("COMMENT", commentId, "REPORT_CANCEL", TextUtils.truncate(report.getComment().getContent()));
        });
    }

}
