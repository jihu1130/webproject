package com.webschool.webschool.school.service;

import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.dto.AdminScheduleCommentSummaryDto;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 신고 관리 탭 - 게시글/댓글 신고 관리와 동일한 패턴
    public List<AdminScheduleCommentSummaryDto> getReportedComments(String keyword) {
        return scheduleCommentRepository.findReportedOrBlindComments().stream()
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

    @Transactional
    public void setBlind(Long id, boolean blind) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setBlind(blind);
        if (blind) {
            // 다시 블라인드 처리한다는 건 "문제없음" 판결을 뒤집는 것과 같다
            comment.setReportCleared(false);
        }
    }

    @Transactional
    public void clearReport(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setReportCleared(true);
        comment.setBlind(false);
    }

    // "문제없음" 판결 철회 - AdminPostService.unclearReport()와 동일한 패턴
    @Transactional
    public void unclearReport(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setReportCleared(false);
    }

    // 관리자 강제 삭제 - 사용자 본인 삭제와 동일하게 소프트 딜리트로 처리
    @Transactional
    public void deleteComment(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public void restoreComment(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        comment.setDeleted(false);
        comment.setDeletedAt(null);
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
