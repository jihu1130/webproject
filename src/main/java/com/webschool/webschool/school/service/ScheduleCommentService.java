package com.webschool.webschool.school.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.domain.ScheduleCommentBookmark;
import com.webschool.webschool.school.domain.ScheduleCommentLike;
import com.webschool.webschool.school.domain.ScheduleCommentReport;
import com.webschool.webschool.school.dto.ScheduleCommentDto;
import com.webschool.webschool.school.dto.ScheduleCommentReportResultDto;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.school.repository.SchoolRepository;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserBlockService;
import com.webschool.webschool.user.service.UserPenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleCommentService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    // 리치 에디터 도입 이후(2026-08-19) 이 값은 "글자 수"가 아니라 정제된 HTML 문자열 길이 기준이다 -
    // 예전엔 진짜 "한 줄"짜리 300자 제한이었지만, 본문에 사진/동영상/파일 삽입을 지원하면서 넉넉하게 늘림.
    private static final int MAX_CONTENT_LENGTH = 50000;
    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드 (PostCommentService와 동일)
    private static final String BLIND_PLACEHOLDER = "신고 누적으로 블라인드 처리된 한마디입니다.";

    private final ScheduleCommentRepository scheduleCommentRepository;
    private final ScheduleCommentReportRepository scheduleCommentReportRepository;
    private final ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    private final ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final UserPenaltyService userPenaltyService;
    private final UserBlockService userBlockService;
    private final NotificationService notificationService;
    private final AdminActionLogService adminActionLogService;

    // 차단한 사용자의 한마디는 목록에서 걸러낸다(UserBlockService 클래스 주석 참고 - 한마디는 특정
    // "글 작성자"가 없는 공유 스레드라 작성 자체를 막는 방식이 아니라 조회 단계에서 숨기는 방식).
    public List<ScheduleCommentDto> getComments(String atptCode, String schoolCode, LocalDate date,
                                                 String grade, String classNm, String currentUsername) {
        School school = findOrCreateSchool(atptCode, schoolCode);
        Set<Long> blockedUserIds = userBlockService.getBlockedUserIds(currentUsername);
        return scheduleCommentRepository
                .findBySchool_IdAndTargetDateAndGradeAndClassNmAndDeletedFalseOrderByCreatedAtAsc(school.getId(), date, grade, classNm)
                .stream()
                .filter(c -> !blockedUserIds.contains(c.getUser().getId()))
                .map(c -> toDto(c, currentUsername))
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleCommentDto createComment(String atptCode, String schoolCode, LocalDate date,
                                             String grade, String classNm, String username, String content,
                                             boolean pollAttached) {
        String trimmed = validateContent(content, pollAttached);

        School school = findOrCreateSchool(atptCode, schoolCode);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        userPenaltyService.assertCanComment(user);

        ScheduleComment comment = new ScheduleComment();
        comment.setSchool(school);
        comment.setTargetDate(date);
        comment.setGrade(grade);
        comment.setClassNm(classNm);
        comment.setUser(user);
        comment.setContent(trimmed);

        scheduleCommentRepository.save(comment);
        adminActionLogService.log("SCHEDULE_COMMENT", comment.getId(), "CREATE", truncate(HtmlSanitizer.toPlainText(comment.getContent())));
        return toDto(comment, username);
    }

    @Transactional
    public ScheduleCommentDto updateComment(Long id, String username, String content) {
        String trimmed = validateContent(content, false);

        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }

        // 실제로 내용이 바뀐 경우에만 "수정됨"으로 표시 (닉네임 변경 등 무관한 변경이나
        // 내용 그대로 재저장한 경우에는 updatedAt을 건드리지 않는다)
        if (!trimmed.equals(comment.getContent())) {
            comment.setContent(trimmed);
            comment.setUpdatedAt(java.time.LocalDateTime.now());
            // 내용이 바뀌었으니 예전 "문제없음" 판결은 더 이상 유효하지 않다 - 다시 검토가 필요함
            comment.setReportCleared(false);
            adminActionLogService.log("SCHEDULE_COMMENT", comment.getId(), "UPDATE", truncate(HtmlSanitizer.toPlainText(trimmed)));
        }

        return toDto(comment, username);
    }

    @Transactional
    public void deleteComment(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경 (관리자 페이지에서 계속 조회/복구 가능, PostComment와 동일 패턴)
        comment.setDeleted(true);
        comment.setDeletedAt(java.time.LocalDateTime.now());
        adminActionLogService.log("SCHEDULE_COMMENT", comment.getId(), "DELETE", truncate(HtmlSanitizer.toPlainText(comment.getContent())));
    }

    @Transactional
    public ScheduleCommentReportResultDto reportComment(Long id, String username, String reason) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (comment.isReportCleared()) {
            throw new IllegalArgumentException("이미 검토되어 문제없다고 판정된 한마디입니다.");
        }

        if (comment.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글은 신고할 수 없습니다.");
        }

        if (scheduleCommentReportRepository.existsByComment_IdAndReporter_Username(id, username)) {
            throw new IllegalArgumentException("이미 신고한 댓글입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }

        ScheduleCommentReport report = new ScheduleCommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        scheduleCommentReportRepository.save(report);
        adminActionLogService.log("SCHEDULE_COMMENT", id, "REPORT",
                trimmedReason != null ? truncate(trimmedReason) : truncate(HtmlSanitizer.toPlainText(comment.getContent())));

        scheduleCommentRepository.incrementReportCount(id);
        int displayReportCount = comment.getReportCount() + 1;
        boolean nowBlind = comment.isBlind();
        if (!nowBlind && displayReportCount >= BLIND_THRESHOLD) {
            comment.setBlind(true);
            nowBlind = true;
        }

        return new ScheduleCommentReportResultDto(displayReportCount, nowBlind);
    }

    // 신고 취소 - PostService.cancelReport()와 동일한 이유/패턴(자동 언블라인드는 하지 않음).
    @Transactional
    public void cancelReport(Long id, String username) {
        scheduleCommentReportRepository.findByComment_IdAndReporter_Username(id, username).ifPresent(report -> {
            scheduleCommentReportRepository.delete(report);
            scheduleCommentRepository.decrementReportCount(id);
            adminActionLogService.log("SCHEDULE_COMMENT", id, "REPORT_CANCEL", truncate(HtmlSanitizer.toPlainText(report.getComment().getContent())));
        });
    }

    // PostService.toggleLike()/toggleBookmark()와 동일한 패턴
    @Transactional
    public Map<String, Object> toggleLike(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

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
                    "/school/comments/" + comment.getId());
        }
        return Map.of("liked", liked, "likeCount", displayLikeCount);
    }

    @Transactional
    public boolean toggleBookmark(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

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

    // 마이페이지 "북마크" 탭(한마디)의 "해제" 버튼 전용 - PostService.removeBookmark()와 동일한 이유로
    // 토글이 아닌 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeBookmark(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        scheduleCommentBookmarkRepository.findByComment_IdAndUser_Id(id, user.getId())
                .ifPresent(scheduleCommentBookmarkRepository::delete);
    }

    // 마이페이지 "좋아요" 탭(한마디)의 "취소" 버튼 전용 - PostService.removeLike()와 동일한 이유로
    // 토글이 아닌 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeLike(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        scheduleCommentLikeRepository.findByComment_IdAndUser_Id(id, user.getId()).ifPresent(like -> {
            scheduleCommentLikeRepository.delete(like);
            scheduleCommentRepository.decrementLikeCount(id);
        });
    }

    // 게시글 본문에 삽입된 "한마디로 바로가기" 임베드 카드가 가리키는 대상 조회용
    // (SchoolController.openComment()에서 캘린더 화면으로 리다이렉트하는 데 필요한 정보를 얻는다).
    public ScheduleComment findForPermalink(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        if (comment.isDeleted()) {
            throw new IllegalArgumentException("한마디를 찾을 수 없습니다.");
        }
        return comment;
    }

    // 수정 페이지(GET /school/comments/{id}/edit) 진입 시 폼에 기존 내용/컨텍스트를 채우기 위한 조회.
    // updateComment()와 동일한 소유권 검증을 미리 수행해서, 남의 한마디 수정 페이지 URL을 직접 쳐서
    // 들어와도 내용이 노출되지 않게 막는다(PostController.editForm()의 postService.getForEdit()와 동일 패턴).
    public ScheduleComment getForEdit(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("한마디를 찾을 수 없습니다."));
        if (comment.isDeleted()) {
            throw new IllegalArgumentException("한마디를 찾을 수 없습니다.");
        }
        if (!comment.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 한마디만 수정할 수 있습니다.");
        }
        return comment;
    }

    // PostService.isAdmin()과 동일한 버그 수정 - ROLE_ADMIN만 확인하면 총관리자(ROLE_SUPER_ADMIN)가
    // 블라인드된 한마디 원본을 캘린더 화면에서 못 보고 관리자 페이지를 거쳐야 하는 문제가 있었다.
    private boolean isAdmin(String username) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(User::isAdmin)
                .orElse(false);
    }

    // PostService.truncate()와 동일한 용도 - 감사 로그 detail(VARCHAR 300)에 넣기 전 요약.
    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    // PostService.validateContent()와 동일한 정제 로직 - th:utext로 그대로 렌더링하므로 이 단계가
    // 유일한 XSS 방어선이다. pollAttached: 설문이 함께 첨부되면 내용이 비어도 통과(PostService와 동일 규칙).
    // 금지어 검사(BannedWordFilter)가 빠져있던 걸 발견해서 PostService와 동일하게 추가함(2026-08-28).
    private String validateContent(String content, boolean pollAttached) {
        if (content == null || content.isBlank()) {
            if (pollAttached) {
                return "";
            }
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        String sanitized = HtmlSanitizer.sanitize(content.trim());
        String plainText = HtmlSanitizer.toPlainText(sanitized);
        if (plainText.isBlank() && !sanitized.contains("<img") && !sanitized.contains("<video")) {
            if (pollAttached) {
                return sanitized;
            }
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (sanitized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        BannedWordFilter.validate(plainText);
        return sanitized;
    }

    private School findOrCreateSchool(String atptCode, String schoolCode) {
        return schoolRepository.findBySdSchulCode(schoolCode)
                .orElseGet(() -> schoolRepository.save(School.builder()
                        .atptOfcdcScCode(atptCode)
                        .sdSchulCode(schoolCode)
                        .schoolName("우리 학교")
                        .build()));
    }

    private ScheduleCommentDto toDto(ScheduleComment c, String currentUsername) {
        boolean mine = currentUsername != null && c.getUser().getUsername().equals(currentUsername);
        // 블라인드된 한마디는 작성자 본인/관리자에게만 원본 내용을 보여준다 (PostCommentService와 동일 패턴)
        String content = c.isBlind() && !mine && !isAdmin(currentUsername) ? BLIND_PLACEHOLDER : c.getContent();
        boolean reportedByMe = !mine && currentUsername != null
                && scheduleCommentReportRepository.existsByComment_IdAndReporter_Username(c.getId(), currentUsername);
        boolean likedByMe = currentUsername != null
                && scheduleCommentLikeRepository.existsByComment_IdAndUser_Username(c.getId(), currentUsername);
        boolean bookmarkedByMe = currentUsername != null
                && scheduleCommentBookmarkRepository.existsByComment_IdAndUser_Username(c.getId(), currentUsername);

        return ScheduleCommentDto.builder()
                .id(c.getId())
                .nickname(c.getUser().isDeleted() ? "탈퇴한 사용자" : c.getUser().getNickname())
                .authorId(c.getUser().getId())
                .authorLinkable(!c.getUser().isDeleted())
                .content(content)
                .createdAt(c.getCreatedAt().format(DISPLAY_FORMAT))
                .edited(c.getUpdatedAt() != null)
                .mine(mine)
                .blind(c.isBlind())
                .reportedByMe(reportedByMe)
                .likeCount(c.getLikeCount())
                .likedByMe(likedByMe)
                .bookmarkedByMe(bookmarkedByMe)
                .build();
    }
}
