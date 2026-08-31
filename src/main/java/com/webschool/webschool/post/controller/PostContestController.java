package com.webschool.webschool.post.controller;

import com.webschool.webschool.post.dto.ContestWeekResultDto;
import com.webschool.webschool.post.dto.PostContestEntryDto;
import com.webschool.webschool.post.service.PostContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/posts/contest")
@RequiredArgsConstructor
public class PostContestController {

    private final PostContestService postContestService;

    @GetMapping
    public String list(Authentication authentication, Model model) {
        List<PostContestEntryDto> entries = postContestService.getCurrentWeekEntries(authentication.getName());
        model.addAttribute("entries", entries);
        model.addAttribute("weekDeadline", postContestService.currentWeekDeadline());
        return "post/contest-list";
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<ContestWeekResultDto> weeks = postContestService.getResultHistory(page, 8);
        model.addAttribute("weeks", weeks);
        return "post/contest-history";
    }

    @PostMapping("/nominate")
    public String nominate(@RequestParam String postUuid, Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            postContestService.nominate(postUuid, authentication.getName());
            redirectAttributes.addFlashAttribute("flashSuccess", "이번 주 인기 게시글 후보로 신청했어요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/posts/" + postUuid;
    }

    @PostMapping("/entries/{id}/vote")
    @ResponseBody
    public ResponseEntity<Void> vote(@PathVariable Long id, Authentication authentication) {
        postContestService.vote(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
