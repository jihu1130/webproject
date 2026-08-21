package com.webschool.webschool.main.controller;

import com.webschool.webschool.notice.dto.NoticeDto;
import com.webschool.webschool.notice.service.NoticeService;
import com.webschool.webschool.post.dto.PostListItemDto;
import com.webschool.webschool.post.service.PostService;
import com.webschool.webschool.school.dto.CalendarEventDto;
import com.webschool.webschool.school.service.SchoolService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 통합 검색 - 커뮤니티(PostRepository.search 재사용)/공지(NoticeService.getHistory 재사용)/
// 학사일정(SchoolService.findNearestEvent 재사용)을 한 화면에서 훑어본다. 각 도메인은 이미 자기
// 목록/검색 화면이 따로 있으므로, 여기서는 새 검색 로직을 만들지 않고 기존 서비스 메서드를 그대로
// 재사용해 미리보기(최대 5개)만 보여주고 "더보기"는 해당 화면의 검색 결과로 넘긴다.
// 학사일정은 학교마다 다른 데이터라 로그인 + 학교 설정이 끝난 사용자에게만 보여준다.
@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private static final int PREVIEW_SIZE = 5;

    private final PostService postService;
    private final NoticeService noticeService;
    private final SchoolService schoolService;
    private final UserService userService;

    @GetMapping
    public String search(@RequestParam(required = false) String keyword,
                          Authentication authentication, Model model) {
        model.addAttribute("keyword", keyword);
        if (keyword == null || keyword.isBlank()) {
            return "search/results";
        }

        Page<PostListItemDto> posts = postService.getList(0, null, keyword, PREVIEW_SIZE, "", "");
        model.addAttribute("posts", posts.getContent());
        model.addAttribute("postsTotal", posts.getTotalElements());

        Page<NoticeDto> notices = noticeService.getHistory(0, PREVIEW_SIZE, keyword);
        model.addAttribute("notices", notices.getContent());
        model.addAttribute("noticesTotal", notices.getTotalElements());

        model.addAttribute("event", findScheduleEvent(authentication, keyword));

        return "search/results";
    }

    // 캘린더 일정은 "내 학교" 기준으로만 조회 가능하다 - 비로그인이거나 아직 학교 설정을 안 마친
    // 계정(User.needsSchoolSetup())은 이 영역을 건너뛴다(검색 결과에서 조용히 빠짐, 에러 아님).
    private CalendarEventDto findScheduleEvent(Authentication authentication, String keyword) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        User user = userService.getByUsername(authentication.getName());
        if (user.getAtptCode() == null || user.getSchoolCode() == null) {
            return null;
        }
        return schoolService.findNearestEvent(user.getAtptCode(), user.getSchoolCode(), keyword);
    }
}
