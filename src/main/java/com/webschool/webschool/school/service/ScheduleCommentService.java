package com.webschool.webschool.school.service;

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
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleCommentService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_CONTENT_LENGTH = 300;
    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드 (PostCommentService와 동일)
    private static final String BLIND_PLACEHOLDER = "신고 누적으로 블라인드 처리된 한마디입니다.";

    private final ScheduleCommentRepository scheduleCommentRepository;
    private final ScheduleCommentReportRepository scheduleCommentReportRepository;
    private final ScheduleCommentLikeRepository scheduleCommentLikeRepository;
    private final ScheduleCommentBookmarkRepository scheduleCommentBookmarkRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public List<ScheduleCommentDto> getComments(String atptCode, String schoolCode, LocalDate date,
                                                 String grade, String classNm, String currentUsername) {
        School school = findOrCreateSchool(atptCode, schoolCode);
        return scheduleCommentRepository
                .findBySchool_IdAndTargetDateAndGradeAndClassNmAndDeletedFalseOrderByCreatedAtAsc(school.getId(), date, grade, classNm)
                .stream()
                .map(c -> toDto(c, currentUsername))
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleCommentDto createComment(String atptCode, String schoolCode, LocalDate date,
                                             String grade, String classNm, String username, String content) {
        String trimmed = validateContent(content);

        School school = findOrCreateSchool(atptCode, schoolCode);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        ScheduleComment comment = new ScheduleComment();
        comment.setSchool(school);
        comment.setTargetDate(date);
        comment.setGrade(grade);
        comment.setClassNm(classNm);
        comment.setUser(user);
        comment.setContent(trimmed);

        scheduleCommentRepository.save(comment);
        return toDto(comment, username);
    }

    @Transactional
    public ScheduleCommentDto updateComment(Long id, String username, String content) {
        String trimmed = validateContent(content);

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

        comment.setReportCount(comment.getReportCount() + 1);
        if (comment.getReportCount() >= BLIND_THRESHOLD) {
            comment.setBlind(true);
        }

        return new ScheduleCommentReportResultDto(comment.getReportCount(), comment.isBlind());
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
        if (existing.isPresent()) {
            scheduleCommentLikeRepository.delete(existing.get());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            liked = false;
        } else {
            ScheduleCommentLike like = new ScheduleCommentLike();
            like.setComment(comment);
            like.setUser(user);
            scheduleCommentLikeRepository.save(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            liked = true;
        }
        return Map.of("liked", liked, "likeCount", comment.getLikeCount());
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
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        });
    }

    private boolean isAdmin(String username) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(u -> u.getRole() == User.Role.ROLE_ADMIN)
                .orElse(false);
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        return content.trim();
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
                .nickname(c.getUser().getNickname())
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
