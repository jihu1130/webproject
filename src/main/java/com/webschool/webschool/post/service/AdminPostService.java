package com.webschool.webschool.post.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostComment;
import com.webschool.webschool.post.domain.PostReport;
import com.webschool.webschool.post.dto.AdminCommentItemDto;
import com.webschool.webschool.post.dto.AdminCommentReportSummaryDto;
import com.webschool.webschool.post.dto.AdminPostDetailDto;
import com.webschool.webschool.post.dto.AdminPostSummaryDto;
import com.webschool.webschool.post.dto.AdminReportItemDto;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 관리자(ROLE_ADMIN) 전용 게시물 관리 로직. 기존 PostService(일반 사용자 플로우)는 건드리지 않고 분리했다.
@Service
@RequiredArgsConstructor
public class AdminPostService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostImageService postImageService;
    private final NotificationService notificationService;
    private final AdminActionLogService adminActionLogService;

    // from/to(둘 다 선택)로 신고 날짜 범위를 좁힐 수 있다 - 버그 수정: 예전엔 키워드 검색만 있고
    // "이번 주 신고만 보기" 같은 날짜 필터가 없었다(AdminScheduleCommentService.getReportedComments()
    // 와 동일 패턴).
    public List<AdminPostSummaryDto> getReportedPosts(String keyword, LocalDate from, LocalDate to) {
        return postRepository.findReportedOrBlindPosts().stream()
                .filter(p -> matchesDateRange(p.getCreatedAt(), from, to))
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getTitle(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 신고 관리 > 댓글 탭 - 게시글 신고 관리와 동일한 패턴, 게시글 아이디로 상세(부모 게시글) 이동
    public List<AdminCommentReportSummaryDto> getReportedComments(String keyword, LocalDate from, LocalDate to) {
        return postCommentRepository.findReportedOrBlindComments().stream()
                .filter(c -> matchesDateRange(c.getCreatedAt(), from, to))
                .map(this::toCommentReportSummaryDto)
                .filter(dto -> matches(keyword, dto.getContent(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 댓글 관리 "전체" 탭 - 신고 여부와 무관하게 삭제되지 않은 전체 댓글 (게시글의 getAllPosts()와
    // 같은 이유로 추가 - 평소엔 신고된 댓글만 보였어서 관리자가 평범한 댓글을 둘러볼 방법이 없었음)
    public List<AdminCommentReportSummaryDto> getAllComments(String keyword) {
        return postCommentRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toCommentReportSummaryDto)
                .filter(dto -> matches(keyword, dto.getContent(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 댓글 관리 "삭제됨" 탭
    public List<AdminCommentReportSummaryDto> getDeletedComments(String keyword) {
        return postCommentRepository.findAllByDeletedTrueOrderByDeletedAtDesc().stream()
                .map(this::toCommentReportSummaryDto)
                .filter(dto -> matches(keyword, dto.getContent(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 소프트 삭제된 게시물 목록 (6-6 항목 - 관리자 페이지 "삭제됨" 탭)
    public List<AdminPostSummaryDto> getDeletedPosts(String keyword) {
        return postRepository.findAllByDeletedTrueOrderByDeletedAtDesc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getTitle(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 신고 여부와 무관하게 삭제되지 않은 전체 게시물 목록 - "전체 게시글" 탭
    // (기존엔 신고/블라인드된 글만 관리자 화면에 보였는데, 평소 문제 없는 글도 미리 둘러볼 수 있어야 한다는 요구로 추가)
    public List<AdminPostSummaryDto> getAllPosts(String keyword) {
        return postRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toSummaryDto)
                .filter(dto -> matches(keyword, dto.getTitle(), dto.getAuthorNickname()))
                .collect(Collectors.toList());
    }

    // 관리자 목록 검색 - 데이터 규모가 작아 DB 쿼리 대신 조회 후 메모리에서 필터링(제목/내용/작성자 대상)
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

    // 소프트 삭제된 게시물도 id로 직접 조회 가능 (postRepository.findById()는 deleted 여부를 필터링하지 않음)
    public AdminPostDetailDto getPostDetail(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        List<AdminReportItemDto> reports = postReportRepository.findByPost_IdOrderByCreatedAtDesc(id).stream()
                .map(this::toReportItemDto)
                .collect(Collectors.toList());

        return AdminPostDetailDto.builder()
                .id(post.getId())
                .uuid(post.getUuid())
                .title(post.getTitle())
                .content(post.getContent())
                .authorNickname(post.getAuthor().getNickname())
                .authorId(post.getAuthor().getId())
                .authorUsername(post.getAuthor().getUsername())
                .category(post.getCategory().name())
                .categoryLabel(post.getCategory().getLabel())
                .viewCount(post.getViewCount())
                .reportCount(post.getReportCount())
                .blind(post.isBlind())
                .reportCleared(post.isReportCleared())
                .deleted(post.isDeleted())
                .deletedAt(post.getDeletedAt() != null ? post.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .createdAt(post.getCreatedAt().format(DISPLAY_FORMAT))
                .edited(post.getUpdatedAt() != null)
                .reports(reports)
                .images(postImageService.getImages(id))
                .comments(postCommentRepository.findByPost_IdOrderByCreatedAtAsc(id).stream()
                        .map(this::toCommentItemDto)
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public void setBlind(Long id, boolean blind) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        post.setBlind(blind);
        if (blind) {
            // 다시 블라인드 처리한다는 건 "문제없음" 판결을 뒤집는 것과 같다
            post.setReportCleared(false);
            notificationService.notify(post.getAuthor(), Notification.Type.REPORT_ACTION,
                    "'" + truncate(post.getTitle()) + "' 글이 관리자에 의해 블라인드 처리되었습니다.",
                    "/posts/" + post.getUuid());
            adminActionLogService.log("POST", id, "BLIND", truncate(post.getTitle()));
        } else {
            notificationService.notify(post.getAuthor(), Notification.Type.REPORT_ACTION,
                    "'" + truncate(post.getTitle()) + "' 글의 블라인드 처리가 해제되었습니다.",
                    "/posts/" + post.getUuid());
            adminActionLogService.log("POST", id, "UNBLIND", truncate(post.getTitle()));
        }
    }

    // 신고된 게시물을 검토해서 "문제없음"으로 판결 - 재신고해도 카운트/블라인드가 더 이상 발생하지 않고
    // 게시물이 수정되기 전까지 안내 문구만 나간다(PostService.reportPost() 참고)
    @Transactional
    public void clearReport(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        post.setReportCleared(true);
        post.setBlind(false);
        notificationService.notify(post.getAuthor(), Notification.Type.REPORT_ACTION,
                "'" + truncate(post.getTitle()) + "' 글이 검토 결과 문제없음으로 처리되었습니다.",
                "/posts/" + post.getUuid());
        adminActionLogService.log("POST", id, "REPORT_CLEAR", truncate(post.getTitle()));
    }

    // "문제없음" 판결 철회 - 잘못 눌렀거나 재검토가 필요할 때 되돌리는 용도. reportCount는 그대로
    // 남아있으므로 철회하면 다시 "신고누적" 상태로 보인다(clearReport와 반대 동작).
    @Transactional
    public void unclearReport(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        post.setReportCleared(false);
        adminActionLogService.log("POST", id, "REPORT_UNCLEAR", truncate(post.getTitle()));
    }

    // 댓글 신고 "문제없음" 판결 - 게시물과 동일한 패턴
    @Transactional
    public void clearCommentReport(Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        comment.setReportCleared(true);
        comment.setBlind(false);
        notificationService.notify(comment.getAuthor(), Notification.Type.REPORT_ACTION,
                "작성하신 댓글이 검토 결과 문제없음으로 처리되었습니다.",
                "/posts/" + comment.getPost().getUuid());
        adminActionLogService.log("COMMENT", commentId, "REPORT_CLEAR", null);
    }

    // 댓글 신고 "문제없음" 판결 철회 - 게시물과 동일한 패턴
    @Transactional
    public void unclearCommentReport(Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        comment.setReportCleared(false);
        adminActionLogService.log("COMMENT", commentId, "REPORT_UNCLEAR", null);
    }

    // 아래 3개는 "댓글 관리"(/admin/comments) 전용 화면에서 댓글 하나를 게시글 상세로 들어가지
    // 않고 목록에서 바로 조치하기 위한 메서드 - 게시물의 setBlind()/deletePost()/restorePost()와
    // 완전히 동일한 패턴이다.
    @Transactional
    public void setCommentBlind(Long commentId, boolean blind) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        comment.setBlind(blind);
        if (blind) {
            comment.setReportCleared(false);
            notificationService.notify(comment.getAuthor(), Notification.Type.REPORT_ACTION,
                    "작성하신 댓글이 관리자에 의해 블라인드 처리되었습니다.",
                    "/posts/" + comment.getPost().getUuid());
            adminActionLogService.log("COMMENT", commentId, "BLIND", null);
        } else {
            notificationService.notify(comment.getAuthor(), Notification.Type.REPORT_ACTION,
                    "작성하신 댓글의 블라인드 처리가 해제되었습니다.",
                    "/posts/" + comment.getPost().getUuid());
            adminActionLogService.log("COMMENT", commentId, "UNBLIND", null);
        }
    }

    @Transactional
    public void deleteComment(Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        adminActionLogService.log("COMMENT", commentId, "DELETE", null);
    }

    @Transactional
    public void restoreComment(Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        comment.setDeleted(false);
        comment.setDeletedAt(null);
        adminActionLogService.log("COMMENT", commentId, "RESTORE", null);
    }

    // 관리자 강제 삭제 - 사용자 본인 삭제와 동일하게 소프트 딜리트로 처리한다(누구나의 글이든 삭제 가능하다는 점만 다름).
    // 이렇게 해야 삭제된 글도 "삭제됨" 탭에서 계속 조회/복구할 수 있다(6-6 항목).
    @Transactional
    public void deletePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        adminActionLogService.log("POST", id, "DELETE", truncate(post.getTitle()));
    }

    // 소프트 삭제 복구
    @Transactional
    public void restorePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        post.setDeleted(false);
        post.setDeletedAt(null);
        adminActionLogService.log("POST", id, "RESTORE", truncate(post.getTitle()));
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private AdminPostSummaryDto toSummaryDto(Post post) {
        List<PostReport> reports = postReportRepository.findByPost_IdOrderByCreatedAtDesc(post.getId());
        String latestReportAt = reports.isEmpty() ? null : reports.get(0).getCreatedAt().format(DISPLAY_FORMAT);
        // 게시물 자체는 신고가 없어도 그 밑에 신고된/블라인드된 댓글이 있을 수 있어서, 목록에서
        // "댓글 신고 있음" 배지를 보여주기 위해 함께 계산한다(사용자 지적 - 8번 항목 갱신 참고).
        long reportedCommentCount = postCommentRepository.countReportedOrBlindByPostId(post.getId());

        return AdminPostSummaryDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .category(post.getCategory().name())
                .categoryLabel(post.getCategory().getLabel())
                .authorNickname(post.getAuthor().getNickname())
                .authorId(post.getAuthor().getId())
                .reportCount(post.getReportCount())
                .blind(post.isBlind())
                .reportCleared(post.isReportCleared())
                .deleted(post.isDeleted())
                .createdAt(post.getCreatedAt().format(DISPLAY_FORMAT))
                .latestReportAt(latestReportAt)
                .deletedAt(post.getDeletedAt() != null ? post.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .reportedCommentCount((int) reportedCommentCount)
                .build();
    }

    private AdminCommentReportSummaryDto toCommentReportSummaryDto(PostComment comment) {
        return AdminCommentReportSummaryDto.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .postTitle(comment.getPost().getTitle())
                .content(comment.getContent())
                .authorNickname(comment.getAuthor().getNickname())
                .authorId(comment.getAuthor().getId())
                .reportCount(comment.getReportCount())
                .blind(comment.isBlind())
                .reportCleared(comment.isReportCleared())
                .createdAt(comment.getCreatedAt().format(DISPLAY_FORMAT))
                .deleted(comment.isDeleted())
                .deletedAt(comment.getDeletedAt() != null ? comment.getDeletedAt().format(DISPLAY_FORMAT) : null)
                .build();
    }

    private AdminCommentItemDto toCommentItemDto(PostComment comment) {
        return AdminCommentItemDto.builder()
                .id(comment.getId())
                .authorNickname(comment.getAuthor().getNickname())
                .authorId(comment.getAuthor().getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().format(DISPLAY_FORMAT))
                .deleted(comment.isDeleted())
                .reportCount(comment.getReportCount())
                .blind(comment.isBlind())
                .reportCleared(comment.isReportCleared())
                .build();
    }

    private AdminReportItemDto toReportItemDto(PostReport report) {
        return AdminReportItemDto.builder()
                .reporterId(report.getReporter().getId())
                .reporterNickname(report.getReporter().getNickname())
                .reporterUsername(report.getReporter().getUsername())
                .reason(report.getReason())
                .createdAt(report.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
