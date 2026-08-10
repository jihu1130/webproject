package com.webschool.webschool.post.service;

import com.webschool.webschool.post.domain.CommentReport;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostComment;
import com.webschool.webschool.post.dto.CommentReportResultDto;
import com.webschool.webschool.post.dto.PostCommentDto;
import com.webschool.webschool.post.repository.CommentReportRepository;
import com.webschool.webschool.post.repository.PostCommentRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostCommentService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드 (PostService와 동일)
    private static final String BLIND_PLACEHOLDER = "신고 누적으로 블라인드 처리된 댓글입니다.";

    private final PostCommentRepository postCommentRepository;
    private final CommentReportRepository commentReportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public List<PostCommentDto> getComments(Long postId, String currentUsername) {
        return postCommentRepository.findByPost_IdAndDeletedFalseOrderByCreatedAtAsc(postId)
                .stream()
                .map(c -> toDto(c, currentUsername))
                .collect(Collectors.toList());
    }

    @Transactional
    public PostCommentDto createComment(Long postId, String username, String content) {
        String trimmed = validateContent(content);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        PostComment comment = new PostComment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(trimmed);

        postCommentRepository.save(comment);
        return toDto(comment, username);
    }

    @Transactional
    public PostCommentDto updateComment(Long commentId, String username, String content) {
        String trimmed = validateContent(content);

        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }

        if (!trimmed.equals(comment.getContent())) {
            comment.setContent(trimmed);
            comment.setUpdatedAt(LocalDateTime.now());
            // 내용이 바뀌었으니 예전 "문제없음" 판결은 더 이상 유효하지 않다 - 다시 검토가 필요함
            comment.setReportCleared(false);
        }

        return toDto(comment, username);
    }

    @Transactional
    public void deleteComment(Long commentId, String username) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경 (관리자 페이지에서 계속 조회 가능, 6-6 항목 참고)
        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public CommentReportResultDto reportComment(Long commentId, String username, String reason) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (comment.isDeleted()) {
            throw new IllegalArgumentException("댓글을 찾을 수 없습니다.");
        }

        if (comment.isReportCleared()) {
            throw new IllegalArgumentException("이미 검토되어 문제없다고 판정된 댓글입니다.");
        }

        if (comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 댓글은 신고할 수 없습니다.");
        }

        if (commentReportRepository.existsByComment_IdAndReporter_Username(commentId, username)) {
            throw new IllegalArgumentException("이미 신고한 댓글입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }

        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        commentReportRepository.save(report);

        comment.setReportCount(comment.getReportCount() + 1);
        if (comment.getReportCount() >= BLIND_THRESHOLD) {
            comment.setBlind(true);
        }

        return new CommentReportResultDto(comment.getReportCount(), comment.isBlind());
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
        String trimmed = content.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    private PostCommentDto toDto(PostComment c, String currentUsername) {
        boolean mine = currentUsername != null && c.getAuthor().getUsername().equals(currentUsername);
        // 블라인드된 댓글은 작성자 본인/관리자에게만 원본 내용을 보여준다 (PostService의 블라인드 가시성 판단과 동일 패턴)
        String content = c.isBlind() && !mine && !isAdmin(currentUsername) ? BLIND_PLACEHOLDER : c.getContent();

        return PostCommentDto.builder()
                .id(c.getId())
                .nickname(c.getAuthor().isDeleted() ? "탈퇴한 사용자" : c.getAuthor().getNickname())
                .content(content)
                .createdAt(c.getCreatedAt().format(DISPLAY_FORMAT))
                .edited(c.getUpdatedAt() != null)
                .mine(mine)
                .blind(c.isBlind())
                .build();
    }
}
