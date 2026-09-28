package com.webschool.webschool.school.service;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.error.ErrorCode;
import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.school.domain.School;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.dto.ScheduleCommentDto;
import com.webschool.webschool.school.repository.ScheduleCommentBookmarkRepository;
import com.webschool.webschool.school.repository.ScheduleCommentLikeRepository;
import com.webschool.webschool.school.repository.ScheduleCommentReportRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import com.webschool.webschool.school.repository.SchoolRepository;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.post.util.BannedWordFilter;
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
import java.util.Set;
import java.util.stream.Collectors;

// 오늘의 한마디 목록/작성/수정/삭제. 2026-09-28 파일 정리 때 신고(ScheduleCommentReportService)와
// 좋아요·북마크(ScheduleCommentReactionService)를 분리했다 - PostService/PostCommentService와 같은 축.
@Service
@RequiredArgsConstructor
public class ScheduleCommentService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    // 리치 에디터 도입 이후(2026-08-19) 이 값은 "글자 수"가 아니라 정제된 HTML 문자열 길이 기준이다 -
    // 예전엔 진짜 "한 줄"짜리 300자 제한이었지만, 본문에 사진/동영상/파일 삽입을 지원하면서 넉넉하게 늘림.
    private static final int MAX_CONTENT_LENGTH = 50000;
    private static final String BLIND_PLACEHOLDER = "신고 누적으로 블라인드 처리된 한마디입니다.";

    private final ScheduleCommentRepository scheduleCommentRepository;
    private final ScheduleCommentReportRepository scheduleCommentReportRepository;
    private final ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    private final ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final UserPenaltyService userPenaltyService;
    private final UserBlockService userBlockService;
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
                .filter(c -> c.getUser() == null || !blockedUserIds.contains(c.getUser().getId()))
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
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (comment.getUser() == null || !comment.getUser().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 댓글만 수정할 수 있습니다.");
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
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (comment.getUser() == null || !comment.getUser().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경 (관리자 페이지에서 계속 조회/복구 가능, PostComment와 동일 패턴)
        comment.setDeleted(true);
        comment.setDeletedAt(java.time.LocalDateTime.now());
        adminActionLogService.log("SCHEDULE_COMMENT", comment.getId(), "DELETE", truncate(HtmlSanitizer.toPlainText(comment.getContent())));
    }

    // 공개 URL(/school/comments/{uuid})의 uuid를 내부 Long id로 변환 - PostService.resolveIdByUuid()와
    // 동일한 패턴(컨트롤러 레이어에서만 uuid를 다루고, 그 아래 서비스/리포지토리는 계속 Long을 쓴다).
    public Long resolveIdByUuid(String uuid) {
        return scheduleCommentRepository.findByUuid(uuid)
                .map(ScheduleComment::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다."));
    }

    // 게시글 본문에 삽입된 "한마디로 바로가기" 임베드 카드가 가리키는 대상 조회용
    // (ScheduleCommentController.openComment()에서 캘린더 화면으로 리다이렉트하는 데 필요한 정보를 얻는다).
    public ScheduleComment findForPermalink(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다."));
        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다.");
        }
        return comment;
    }

    // 수정 페이지(GET /school/comments/{id}/edit) 진입 시 폼에 기존 내용/컨텍스트를 채우기 위한 조회.
    // updateComment()와 동일한 소유권 검증을 미리 수행해서, 남의 한마디 수정 페이지 URL을 직접 쳐서
    // 들어와도 내용이 노출되지 않게 막는다(PostController.editForm()의 postService.getForEdit()와 동일 패턴).
    public ScheduleComment getForEdit(Long id, String username) {
        ScheduleComment comment = scheduleCommentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다."));
        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "한마디를 찾을 수 없습니다.");
        }
        if (comment.getUser() == null || !comment.getUser().getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 한마디만 수정할 수 있습니다.");
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
        boolean mine = currentUsername != null && c.getUser() != null && c.getUser().getUsername().equals(currentUsername);
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
                .uuid(c.getUuid())
                .nickname(c.getUser() == null || c.getUser().isDeleted() ? "탈퇴한 사용자" : c.getUser().getNickname())
                .authorId(c.getUser() != null ? c.getUser().getId() : null)
                .authorUuid(c.getUser() != null ? c.getUser().getUuid() : null)
                .authorLinkable(c.getUser() != null && !c.getUser().isDeleted())
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
