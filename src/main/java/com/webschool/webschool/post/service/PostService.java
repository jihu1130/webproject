package com.webschool.webschool.post.service;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostReport;
import com.webschool.webschool.post.dto.PostDetailDto;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.dto.PostListItemDto;
import com.webschool.webschool.post.dto.PostReportResultDto;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.repository.PostReportRepository;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.entity.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int PAGE_SIZE = 10;
    private static final int BLIND_THRESHOLD = 3; // 서로 다른 사용자 3명이 신고하면 자동 블라인드

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;

    public Page<PostListItemDto> getList(int page, Post.Category category, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> result = postRepository.search(category, keyword, pageable);
        return result.map(this::toListItemDto);
    }

    // 공개 URL(/posts/{uuid})을 내부 PK로 변환 - 컨트롤러가 요청을 받자마자 제일 먼저 호출한다
    public Long resolveIdByUuid(String uuid) {
        return postRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."))
                .getId();
    }

    @Transactional
    public PostDetailDto getDetail(Long id, String currentUsername, boolean countView) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        // 소프트 삭제된 게시물은 일반 사용자 화면에서는 완전히 사라진 것처럼 처리 (작성자 본인도 예외 없음).
        // 관리자가 삭제된 글을 봐야 하면 AdminPostService의 별도 경로를 사용한다.
        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        boolean mine = currentUsername != null && post.getAuthor().getUsername().equals(currentUsername);

        // 블라인드 처리된 게시물은 작성자 본인과 관리자만 열람 가능 (그 외에는 존재하지 않는 것처럼 처리)
        if (post.isBlind() && !mine && !isAdmin(currentUsername)) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (countView) {
            post.setViewCount(post.getViewCount() + 1);
        }

        return toDetailDto(post, currentUsername, mine);
    }

    @Transactional
    public String createPost(String username, PostFormDto form) {
        String title = validateTitle(form.getTitle());
        String content = validateContent(form.getContent());
        Post.Category category = parseCategory(form.getCategory());

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        post.setAuthor(author);

        return postRepository.save(post).getUuid();
    }

    public PostFormDto getForEdit(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 수정할 수 있습니다.");
        }

        PostFormDto dto = new PostFormDto();
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setCategory(post.getCategory().name());
        return dto;
    }

    @Transactional
    public void updatePost(Long id, String username, PostFormDto form) {
        String title = validateTitle(form.getTitle());
        String content = validateContent(form.getContent());
        Post.Category category = parseCategory(form.getCategory());

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 수정할 수 있습니다.");
        }

        boolean changed = !title.equals(post.getTitle()) || !content.equals(post.getContent())
                || category != post.getCategory();

        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);

        if (changed) {
            post.setUpdatedAt(LocalDateTime.now());
            // 내용이 바뀌었으니 예전 "문제없음" 판결은 더 이상 유효하지 않다 - 다시 검토가 필요함
            post.setReportCleared(false);
        }
    }

    @Transactional
    public void deletePost(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isDeleted()) {
            throw new IllegalArgumentException("게시물을 찾을 수 없습니다.");
        }

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물만 삭제할 수 있습니다.");
        }

        // 소프트 딜리트: 물리적으로 지우지 않고 상태만 변경한다(댓글/신고/이미지는 그대로 보존되고,
        // 관리자 페이지에서 계속 조회 가능하다 - 6-6 항목 참고). FK 제약 문제도 이걸로 근본 해결됨.
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public PostReportResultDto reportPost(Long id, String username, String reason) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));

        if (post.isReportCleared()) {
            throw new IllegalArgumentException("이미 검토되어 문제없다고 판정된 게시물입니다.");
        }

        if (post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 작성한 게시물은 신고할 수 없습니다.");
        }

        if (postReportRepository.existsByPost_IdAndReporter_Username(id, username)) {
            throw new IllegalArgumentException("이미 신고한 게시물입니다.");
        }

        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }

        PostReport report = new PostReport();
        report.setPost(post);
        report.setReporter(reporter);
        report.setReason(trimmedReason);
        postReportRepository.save(report);

        post.setReportCount(post.getReportCount() + 1);
        if (post.getReportCount() >= BLIND_THRESHOLD) {
            post.setBlind(true);
        }

        return new PostReportResultDto(post.getReportCount(), post.isBlind());
    }

    private boolean isAdmin(String username) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(u -> u.getRole() == User.Role.ROLE_ADMIN)
                .orElse(false);
    }

    private Post.Category parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return Post.Category.FREE;
        }
        try {
            return Post.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 카테고리입니다.");
        }
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + MAX_TITLE_LENGTH + "자 이내로 입력해주세요.");
        }
        String trimmed = title.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("내용은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        String trimmed = content.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    private String displayNickname(Post p) {
        if (p.getCategory() == Post.Category.ANONYMOUS) {
            return "익명";
        }
        return p.getAuthor().isDeleted() ? "탈퇴한 사용자" : p.getAuthor().getNickname();
    }

    private PostListItemDto toListItemDto(Post p) {
        return PostListItemDto.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .title(p.getTitle())
                .nickname(displayNickname(p))
                .category(p.getCategory().name())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(p.getViewCount())
                .build();
    }

    private PostDetailDto toDetailDto(Post p, String currentUsername, boolean mine) {
        return PostDetailDto.builder()
                .id(p.getId())
                .uuid(p.getUuid())
                .title(p.getTitle())
                .content(p.getContent())
                .nickname(displayNickname(p))
                .category(p.getCategory().name())
                .categoryLabel(p.getCategory().getLabel())
                .createdAt(p.getCreatedAt().format(DISPLAY_FORMAT))
                .viewCount(p.getViewCount())
                .reportCount(p.getReportCount())
                .blind(p.isBlind())
                .edited(p.getUpdatedAt() != null)
                .mine(mine)
                .build();
    }
}
