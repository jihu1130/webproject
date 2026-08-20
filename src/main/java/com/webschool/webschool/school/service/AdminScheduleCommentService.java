package com.webschool.webschool.school.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.dto.AdminScheduleCommentSummaryDto;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 관리자(ROLE_ADMIN) 전용 "오늘의 한마디" 관리 로직. 기존 ScheduleCommentService(일반 사용자 플로우)는
// 건드리지 않고 완전히 분리했다 (AdminPostService와 동일 패턴).
@Service
@RequiredArgsConstructor
public class AdminScheduleCommentService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final ScheduleCommentRepository scheduleCommentRepository;
    private final NotificationService notificationService;
    private final AdminActionLogService adminActionLogService;

    // 신고 관리 탭 - 게시글/댓글 신고 관리와 동일한 패턴. from/to(둘 다 선택)로 신고 날짜(작성일 아님 -
    // 이 목록 자체가 "신고 누적/블라인드된" 것만 모은 목록이라 작성일 = 사실상 신고 발생 시점 기준)
    // 범위를 좁힐 수 있다 - 버그 수정: 예전엔 키워드 검색만 있고 "이번 주 신고만 보기" 같은 날짜
    // 필터가 없었다.
    public List<AdminScheduleCommentSummaryDto> getReportedComments(String keyword, LocalDate from, LocalDate to) {
        return scheduleCommentRepository.findReportedOrBlindComments().stream()
                .filter(c -> matchesDateRange(c.getCreatedAt(), from, to))
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getContent(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 삭제되지 않은 전체 한마디 목록 - "전체" 탭
    public List<AdminScheduleCommentSummaryDto> getAllComments(String keyword) {
        return scheduleCommentRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getContent(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 소프트 삭제된 한마디 목록 - "삭제됨" 탭
    public List<AdminScheduleCommentSummaryDto> getDeletedComments(String keyword) {
        return scheduleCommentRepository.findAllByDeletedTrueOrderByDeletedAtDesc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getContent(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    private boolean matches(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase();
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDateRange(LocalDateTime createdAt, LocalDate from, LocalDate to) {
        if (createdAt == null) {
            return true;
        }
        LocalDate date = createdAt.toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    // 버그 수정: AdminPostService.setBlind()/clearReport()는 작성자에게 알림을 보내는데
    // AdminScheduleCommentService만 NotificationService 의존성 자체가 없어서 한마디 신고 처리
    // (블라인드/해제/문제없음)가 작성자에게 전혀 통보되지 않았다 - 여기서도 동일하게 wiring.
    @Transactional
    public void setBlind(Long id, boolean blind) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setBlind(blind);
        String link = "/school/calendar?date=" + comment.getTargetDate() + "&grade=" + comment.getGrade()
                + "&classNm=" + comment.getClassNm();
        if (blind) {
            // 다시 블라인드 처리한다는 건 "문제없음" 판결을 뒤집는 것과 같다
            comment.setReportCleared(false);
            notificationService.notify(comment.getUser(), Notification.Type.REPORT_ACTION,
                    "작성하신 오늘의 한마디가 관리자에 의해 블라인드 처리되었습니다.", link);
            adminActionLogService.log("SCHEDULE_COMMENT", id, "BLIND", null);
        } else {
            notificationService.notify(comment.getUser(), Notification.Type.REPORT_ACTION,
                    "작성하신 오늘의 한마디의 블라인드 처리가 해제되었습니다.", link);
            adminActionLogService.log("SCHEDULE_COMMENT", id, "UNBLIND", null);
        }
    }

    @Transactional
    public void clearReport(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setReportCleared(true);
        comment.setBlind(false);
        notificationService.notify(comment.getUser(), Notification.Type.REPORT_ACTION,
                "작성하신 오늘의 한마디가 검토 결과 문제없음으로 처리되었습니다.",
                "/school/calendar?date=" + comment.getTargetDate() + "&grade=" + comment.getGrade()
                        + "&classNm=" + comment.getClassNm());
        adminActionLogService.log("SCHEDULE_COMMENT", id, "REPORT_CLEAR", null);
    }

    // "문제없음" 판결 철회 - AdminPostService.unclearReport()와 동일한 패턴
    @Transactional
    public void unclearReport(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setReportCleared(false);
        adminActionLogService.log("SCHEDULE_COMMENT", id, "REPORT_UNCLEAR", null);
    }

    // 관리자 강제 삭제 - 사용자 본인 삭제와 동일하게 소프트 딜리트로 처리
    @Transactional
    public void deleteComment(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        adminActionLogService.log("SCHEDULE_COMMENT", id, "DELETE", null);
    }

    @Transactional
    public void restoreComment(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setDeleted(false);
        comment.setDeletedAt(null);
        adminActionLogService.log("SCHEDULE_COMMENT", id, "RESTORE", null);
    }

    private AdminScheduleCommentSummaryDto toSummaryDto(ScheduleComment c) {
        return AdminScheduleCommentSummaryDto.builder()
                .id(c.getId())
                .schoolName(c.getSchool().getSchoolName())
                .targetDate(c.getTargetDate().toString())
                .grade(c.getGrade())
                .classNm(c.getClassNm())
                // 관리자 목록 화면은 여러 건을 한 테이블에 나열하므로 리치 에디터 태그를 걷어낸
                // 순수 텍스트만 보여준다(2026-08-19, PostComment 관리자 목록과 동일 정책).
                .content(HtmlSanitizer.toPlainText(c.getContent()))
                .authorNickname(c.getUser().getNickname())
                .authorId(c.getUser().getId())
                .reportCount(c.getReportCount())
                .blind(c.isBlind())
                .reportCleared(c.isReportCleared())
                .deleted(c.isDeleted())
                .createdAt(c.getCreatedAt().format(DISPLAY_FORMAT))
                .deletedAt(c.getDeletedAt() != null ? c.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .build();
    }
}
