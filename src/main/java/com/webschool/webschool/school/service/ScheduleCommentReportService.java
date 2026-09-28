package com.webschool.webschool.school.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.global.util.TextUtils;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.domain.ScheduleCommentReport;
import com.webschool.webschool.school.dto.ScheduleCommentReportResultDto;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 오늘의 한마디 신고 → 자동 블라인드(PostReportService/CommentReportService와 동일한 패턴,
// CLAUDE.md "신고→자동 블라인드 패턴" 참고). 2026-09-28 ScheduleCommentService에서 분리.
@Service
@RequiredArgsConstructor
public class ScheduleCommentReportService {

    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드 (게시글/댓글과 동일)

    private final ScheduleCommentRepository scheduleCommentRepository;
    private final ScheduleCommentReportRepository scheduleCommentReportRepository;
    private final UserRepository userRepository;
    private final AdminActionLogService adminActionLogService;

    @Transactional
    public ScheduleCommentReportResultDto reportComment(Long id, String username, String reason) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (comment.isReportCleared()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 검토되어 문제없다고 판정된 한마디입니다.");
        }

        if (comment.getUser() != null && comment.getUser().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 댓글은 신고할 수 없습니다.");
        }

        if (scheduleCommentReportRepository.existsByComment_IdAndReporter_Username(id, username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신고한 댓글입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }
        BannedWordFilter.validate(trimmedReason);

        ScheduleCommentReport report = new ScheduleCommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        scheduleCommentReportRepository.save(report);
        adminActionLogService.log("SCHEDULE_COMMENT", id, "REPORT",
                trimmedReason != null ? TextUtils.truncate(trimmedReason) : TextUtils.truncate(HtmlSanitizer.toPlainText(comment.getContent())));

        scheduleCommentRepository.incrementReportCount(id);
        int displayReportCount = comment.getReportCount() + 1;
        boolean nowBlind = comment.isBlind();
        if (!nowBlind && displayReportCount >= BLIND_THRESHOLD) {
            comment.setBlind(true);
            nowBlind = true;
        }

        return new ScheduleCommentReportResultDto(displayReportCount, nowBlind);
    }

    // 신고 취소 - PostReportService.cancelReport()와 동일한 이유/패턴(자동 언블라인드는 하지 않음).
    @Transactional
    public void cancelReport(Long id, String username) {
        scheduleCommentReportRepository.findByComment_IdAndReporter_Username(id, username).ifPresent(report -> {
            scheduleCommentReportRepository.delete(report);
            scheduleCommentRepository.decrementReportCount(id);
            adminActionLogService.log("SCHEDULE_COMMENT", id, "REPORT_CANCEL", TextUtils.truncate(HtmlSanitizer.toPlainText(report.getComment().getContent())));
        });
    }

}
