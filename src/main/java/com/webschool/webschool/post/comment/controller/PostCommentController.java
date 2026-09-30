package com.webschool.webschool.post.comment.controller;

import com.webschool.webschool.global.security.AuthenticationUtils;
import com.webschool.webschool.post.comment.dto.CommentReportResultDto;
import com.webschool.webschool.post.comment.dto.PostCommentDto;
import com.webschool.webschool.post.comment.service.CommentReactionService;
import com.webschool.webschool.post.comment.service.CommentReportService;
import com.webschool.webschool.post.comment.service.PostCommentService;
import com.webschool.webschool.post.service.PostService;
import lombok.RequiredArgsConstructor;
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
    private final CommentReportService commentReportService;
    private final CommentReactionService commentReactionService;
    private final PostService postService;

    @GetMapping
    @ResponseBody
    public List<PostCommentDto> list(@PathVariable String postUuid, Authentication authentication) {
        String username = AuthenticationUtils.usernameOrNull(authentication);
        // 상세 페이지를 못 여는 글(삭제/블라인드/비공개)은 댓글도 못 읽게 같은 조건으로 막는다(보안 점검 M2)
        Long postId = postService.resolveReadableIdByUuid(postUuid, username);
        return postCommentService.getComments(postId, username);
    }

    @PostMapping
    @ResponseBody
    public PostCommentDto create(@PathVariable String postUuid, @RequestParam String content,
                                  @RequestParam(required = false) Long parentId,
                                  Authentication authentication) {
        Long postId = postService.resolveReadableIdByUuid(postUuid, authentication.getName());
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
        CommentReportResultDto result = commentReportService.reportComment(commentId, authentication.getName(), reason);
        return Map.of("success", true, "reportCount", result.reportCount(), "blind", result.blind());
    }

    @PostMapping("/{commentId}/like")
    @ResponseBody
    public Map<String, Object> like(@PathVariable String postUuid, @PathVariable Long commentId,
                                     Authentication authentication) {
        return commentReactionService.toggleLike(commentId, authentication.getName());
    }

    @PostMapping("/{commentId}/bookmark")
    @ResponseBody
    public Map<String, Object> bookmark(@PathVariable String postUuid, @PathVariable Long commentId,
                                         Authentication authentication) {
        boolean bookmarked = commentReactionService.toggleBookmark(commentId, authentication.getName());
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

}
