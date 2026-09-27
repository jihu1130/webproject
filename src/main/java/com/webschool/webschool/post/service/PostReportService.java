package com.webschool.webschool.post.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostReport;
import com.webschool.webschool.post.dto.PostReportResultDto;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 게시글 신고 → 자동 블라인드(CLAUDE.md "신고→자동 블라인드 패턴" - 댓글은 CommentReportService,
// 한마디는 ScheduleCommentService에 같은 패턴이 있다). 2026-09-28 PostService에서 분리.
@Service
@RequiredArgsConstructor
public class PostReportService {

    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminActionLogService adminActionLogService;

    @Transactional
    public PostReportResultDto reportPost(Long id, String username, String reason) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.isReportCleared()) {
            throw new IllegalArgumentException("이미 검토되어 문제없다고 판정된 게시물입니다.");
        }

        if (post.getAuthor() != null && post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물은 신고할 수 없습니다.");
        }

        if (postReportRepository.existsByPost_IdAndReporter_Username(id, username)) {
            throw new IllegalArgumentException("이미 신고한 게시물입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }
        BannedWordFilter.validate(trimmedReason);

        PostReport report = new PostReport();
        report.setPost(post);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        postReportRepository.save(report);
        adminActionLogService.log("POST", id, "REPORT", trimmedReason != null ? truncate(trimmedReason) : truncate(post.getTitle()));

        boolean wasBlind = post.isBlind();
        postRepository.incrementReportCount(id);
        int displayReportCount = post.getReportCount() + 1;
        boolean nowBlind = wasBlind;
        if (!wasBlind && displayReportCount >= BLIND_THRESHOLD) {
            post.setBlind(true);
            nowBlind = true;
            notificationService.notify(post.getAuthor(), Notification.Type.REPORT_ACTION,
                    "'" + truncate(post.getTitle()) + "' 글이 신고 누적으로 블라인드 처리되었습니다.",
                    "/posts/" + post.getUuid());
        }

        return new PostReportResultDto(displayReportCount, nowBlind);
    }

    // 신고 취소 - UserBlockService.unblock()과 동일한 모양(본인 확인 -> 신고 row 삭제). 신고 수만
    // 원자적으로 감소시키고, 이미 블라인드된 글을 자동으로 해제하지는 않는다(취소 한 번으로 조용히
    // 블라인드가 풀리면 신고 누적으로 걸린 조치를 신고자가 스스로 무력화할 수 있어 악용 소지가 있다
    // - 언블라인드는 관리자 "문제없음" 판결로만 가능, AdminPostService 참고).
    @Transactional
    public void cancelReport(Long id, String username) {
        postReportRepository.findByPost_IdAndReporter_Username(id, username).ifPresent(report -> {
            postReportRepository.delete(report);
            postRepository.decrementReportCount(id);
            adminActionLogService.log("POST", id, "REPORT_CANCEL", truncate(report.getPost().getTitle()));
        });
    }

    // 알림 메시지에 제목을 넣을 때 너무 길어지지 않도록 자르는 용도 (Notification.message는 200자 제한)
    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
