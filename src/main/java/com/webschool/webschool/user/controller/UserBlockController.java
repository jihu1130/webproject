package com.webschool.webschool.user.controller;

import com.webschool.webschool.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/users/blocks")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockService userBlockService;

    @PostMapping
    @ResponseBody
    public Map<String, Object> block(@RequestParam Long targetId,
                                      @RequestParam(required = false) Integer durationDays,
                                      Authentication authentication) {
        userBlockService.block(authentication.getName(), targetId, durationDays);
        return Map.of("success", true);
    }

    // 마이페이지 "차단 목록" 탭의 "차단 해제" 버튼 전용 - MyActivityController의 다른 해제
    // 액션들과 동일하게 실패해도 조용히 목록으로 돌아간다.
    @PostMapping("/{id}/unblock")
    public String unblock(@PathVariable Long id, Authentication authentication) {
        try {
            userBlockService.unblock(authentication.getName(), id);
        } catch (IllegalArgumentException ignored) {
        }
        return "redirect:/mypage/activity?tab=blocks";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
