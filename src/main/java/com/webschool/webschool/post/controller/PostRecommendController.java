package com.webschool.webschool.post.controller;

import com.webschool.webschool.post.dto.PostDailyBestResultDto;
import com.webschool.webschool.post.dto.PostRecommendRankDto;
import com.webschool.webschool.post.service.PostRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 추천 게시글 랭킹(2026-09-16, 기존 PostContestController를 대체) - 목록(/posts/contest)은
// 일간/주간/월간/전체 4개 탭을 전부 permitAll로 열어둔다(/posts, /posts/*와 동일한 열람 원칙),
// 추천 액션만 로그인이 필요하다(/posts/** 인증 규칙에 그대로 걸림, SecurityConfig 참고).
@Controller
@RequestMapping("/posts/contest")
@RequiredArgsConstructor
public class PostRecommendController {

    private final PostRecommendService postRecommendService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "DAILY") PostRecommendService.Period period,
                        Authentication authentication, Model model) {
        String viewerUsername = authentication != null ? authentication.getName() : null;
        List<PostRecommendRankDto> ranking = postRecommendService.getRanking(period, viewerUsername);
        model.addAttribute("ranking", ranking);
        model.addAttribute("period", period);
        return "post/contest-list";
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<PostDailyBestResultDto> results = postRecommendService.getDailyBestHistory(page, 10);
        model.addAttribute("results", results);
        return "post/contest-history";
    }

    @PostMapping("/posts/{uuid}/recommend")
    @ResponseBody
    public ResponseEntity<Void> recommend(@PathVariable String uuid, Authentication authentication) {
        postRecommendService.recommend(uuid, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
