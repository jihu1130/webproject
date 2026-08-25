package com.webschool.webschool.post.controller;

import com.webschool.webschool.post.dto.CommentReportResultDto;
import com.webschool.webschool.post.dto.PostCommentDto;
import com.webschool.webschool.post.service.PostCommentService;
import com.webschool.webschool.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/posts/{postUuid}/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService postCommentService;
    private final PostService postService;

    @GetMapping
    @ResponseBody
    public List<PostCommentDto> list(@PathVariable String postUuid, Authentication authentication) {
        Long postId = postService.resolveIdByUuid(postUuid);
        return postCommentService.getComments(postId, extractUsername(authentication));
    }

    @PostMapping
    @ResponseBody
    public PostCommentDto create(@PathVariable String postUuid, @RequestParam String content,
                                  @RequestParam(required = false) Long parentId,
                                  Authentication authentication) {
        Long postId = postService.resolveIdByUuid(postUuid);
        return postCommentService.createComment(postId, authentication.getName(), content, parentId);
    }

    @PutMapping("/{commentId}")
    @ResponseBody
    public PostCommentDto update(@PathVariable String postUuid, @PathVariable Long commentId,
                                  @RequestParam String content, Authentication authentication) {
        return postCommentService.updateComment(commentId, authentication.getName(), content);
    }

    @DeleteMapping("/{commentId}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable String postUuid, @PathVariable Long commentId,
                                       Authentication authentication) {
        postCommentService.deleteComment(commentId, authentication.getName());
        return Map.of("deleted", true);
    }

    @PostMapping("/{commentId}/report")
    @ResponseBody
    public Map<String, Object> report(@PathVariable String postUuid, @PathVariable Long commentId,
                                       @RequestParam(required = false) String reason,
                                       Authentication authentication) {
        CommentReportResultDto result = postCommentService.reportComment(commentId, authentication.getName(), reason);
        return Map.of("success", true, "reportCount", result.reportCount(), "blind", result.blind());
    }

    @PostMapping("/{commentId}/like")
    @ResponseBody
    public Map<String, Object> like(@PathVariable String postUuid, @PathVariable Long commentId,
                                     Authentication authentication) {
        return postCommentService.toggleLike(commentId, authentication.getName());
    }

    @PostMapping("/{commentId}/bookmark")
    @ResponseBody
    public Map<String, Object> bookmark(@PathVariable String postUuid, @PathVariable Long commentId,
                                         Authentication authentication) {
        boolean bookmarked = postCommentService.toggleBookmark(commentId, authentication.getName());
        return Map.of("bookmarked", bookmarked);
    }

    // QNA 답변 채택(네이버 지식인 스타일) - 질문 작성자만 호출 가능, 서비스 단에서 검증하고 실패하면
    // 아래 handleBadRequest()가 처리한다.
    @PostMapping("/{commentId}/accept")
    @ResponseBody
    public Map<String, Object> accept(@PathVariable String postUuid, @PathVariable Long commentId,
                                       Authentication authentication) {
        boolean accepted = postCommentService.acceptAnswer(commentId, authentication.getName());
        return Map.of("accepted", accepted);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
