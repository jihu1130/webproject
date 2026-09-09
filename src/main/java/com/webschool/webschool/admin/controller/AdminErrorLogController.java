package com.webschool.webschool.admin.controller;

import com.webschool.webschool.global.logging.ErrorLogBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 에러 로그 화면 - ErrorLogBuffer(메모리 원형 버퍼, InMemoryErrorAppender가 채움)를 그대로 보여준다.
// 스택 트레이스에 내부 경로/쿼리 파라미터 등이 그대로 노출될 수 있어 대시보드/부하테스트와 동일하게
// 총관리자 전용으로 고정한다(AdminAccessInterceptor 참고, 위임 권한 플래그 없음).
@Controller
@RequestMapping("/admin/error-log")
@RequiredArgsConstructor
public class AdminErrorLogController {

    private final ErrorLogBuffer errorLogBuffer;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("entries", errorLogBuffer.getAll());
        return "admin/error-log";
    }

    @PostMapping("/clear")
    public String clear() {
        errorLogBuffer.clear();
        return "redirect:/admin/error-log";
    }
}
