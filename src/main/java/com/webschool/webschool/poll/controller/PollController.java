package com.webschool.webschool.poll.controller;

import com.webschool.webschool.poll.dto.PollResultDto;
import com.webschool.webschool.poll.dto.PollVoteRequest;
import com.webschool.webschool.poll.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 설문 위젯(post/detail.html, 캘린더 한마디)이 자기 데이터를 스스로 불러오는 전용 API - 알림
// 읽지않음 카운트(/notifications/unread-count)와 동일한 "위젯이 별도 API로 자기 상태를 조회하는"
// 패턴. /polls/** 는 SecurityConfig의 permitAll 목록에 없어 기본적으로 로그인 필요(anyRequest().
// authenticated()) - 비로그인 방문자에게는 애초에 위젯 자체를 렌더링하지 않는다(post/detail.html
// 참고).
@Controller
@RequestMapping("/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @GetMapping("/{id}")
    @ResponseBody
    public PollResultDto result(@PathVariable Long id, Authentication authentication) {
        return pollService.getResult(id, authentication.getName());
    }

    @GetMapping("/by-post/{postId}")
    @ResponseBody
    public ResponseEntity<PollResultDto> byPost(@PathVariable Long postId, Authentication authentication) {
        return pollService.findResultByPost(postId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/by-comment/{commentId}")
    @ResponseBody
    public ResponseEntity<PollResultDto> byComment(@PathVariable Long commentId, Authentication authentication) {
        return pollService.findResultByComment(commentId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/vote")
    @ResponseBody
    public PollResultDto vote(@PathVariable Long id, @RequestBody PollVoteRequest request,
                               Authentication authentication) {
        pollService.vote(id, request.getOptionIds(), request.getCustomOptionText(), authentication.getName());
        return pollService.getResult(id, authentication.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
