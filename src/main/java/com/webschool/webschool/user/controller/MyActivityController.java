package com.webschool.webschool.user.controller;

import com.webschool.webschool.post.service.PostCommentService;
import com.webschool.webschool.post.service.PostService;
import com.webschool.webschool.school.service.ScheduleCommentService;
import com.webschool.webschool.user.dto.MyCommentSummaryDto;
import com.webschool.webschool.user.dto.MyPostSummaryDto;
import com.webschool.webschool.user.dto.MyScheduleCommentSummaryDto;
import com.webschool.webschool.user.service.MyActivityService;
import com.webschool.webschool.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

// 마이페이지 "내 활동내역" - 본인이 작성한 게시글/댓글/오늘의 한마디 + 좋아요/북마크한 게시글·한마디를
// 탭으로 모아보고 그 자리에서 삭제/해제할 수 있게 해준다(검색 포함). 삭제 자체는 기존 자기소유
// 삭제 로직(PostService/PostCommentService/ScheduleCommentService, 모두 username으로 소유자
// 검증함)을 그대로 재사용하고, 이 컨트롤러는 탭/서브탭/페이지/검색어 상태를 유지한 채로 돌아오는
// 리다이렉트만 추가로 책임진다.
@Controller
@RequestMapping("/mypage/activity")
@RequiredArgsConstructor
public class MyActivityController {

    private final MyActivityService myActivityService;
    private final PostService postService;
    private final PostCommentService postCommentService;
    private final ScheduleCommentService scheduleCommentService;
    private final UserBlockService userBlockService;

    @GetMapping
    public String activity(@RequestParam(defaultValue = "posts") String tab,
                            @RequestParam(defaultValue = "post") String type,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) String keyword,
                            Authentication authentication, Model model) {
        String username = authentication.getName();

        if ("comments".equals(tab)) {
            Page<MyCommentSummaryDto> comments = myActivityService.getMyComments(username, page, keyword);
            model.addAttribute("comments", comments);
        } else if ("schedule".equals(tab)) {
            Page<MyScheduleCommentSummaryDto> scheduleComments = myActivityService.getMyScheduleComments(username, page, keyword);
            model.addAttribute("scheduleComments", scheduleComments);
        } else if ("bookmarks".equals(tab)) {
            // 북마크 탭은 게시글/한마디 두 콘텐츠 타입을 다루는 서브탭 구조(신고 관리 화면의
            // type=comment/schedule 패턴과 동일) - 한 화면에 둘 다 페이지네이션하면 "page" 파라미터가
            // 어느 목록 기준인지 애매해지므로 한 번에 하나의 서브탭만 보여준다.
            boolean scheduleType = "schedule".equals(type);
            if (scheduleType) {
                Page<MyScheduleCommentSummaryDto> scheduleBookmarks = myActivityService.getBookmarkedScheduleComments(username, page, keyword);
                model.addAttribute("scheduleBookmarks", scheduleBookmarks);
            } else {
                type = "post";
                Page<MyPostSummaryDto> bookmarks = myActivityService.getBookmarkedPosts(username, page, keyword);
                model.addAttribute("bookmarks", bookmarks);
            }
            model.addAttribute("bookmarkType", type);
        } else if ("likes".equals(tab)) {
            // bookmarks 탭과 동일한 서브탭 구조(게시글/한마디) - 좋아요한 한마디를 볼 방법이 없다는
            // 버그 리포트로 추가됨.
            boolean scheduleType = "schedule".equals(type);
            if (scheduleType) {
                Page<MyScheduleCommentSummaryDto> scheduleLikes = myActivityService.getLikedScheduleComments(username, page, keyword);
                model.addAttribute("scheduleLikes", scheduleLikes);
            } else {
                type = "post";
                Page<MyPostSummaryDto> likes = myActivityService.getLikedPosts(username, page, keyword);
                model.addAttribute("likes", likes);
            }
            model.addAttribute("likeType", type);
        } else if ("blocks".equals(tab)) {
            model.addAttribute("blocks", userBlockService.getMyBlocks(username));
        } else {
            tab = "posts";
            Page<MyPostSummaryDto> posts = myActivityService.getMyPosts(username, page, keyword);
            model.addAttribute("posts", posts);
        }

        model.addAttribute("tab", tab);
        model.addAttribute("keyword", keyword);
        return "user/my-activity";
    }

    @PostMapping("/posts/{uuid}/delete")
    public String deletePost(@PathVariable String uuid, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(required = false) String keyword,
                              Authentication authentication) {
        try {
            postService.deletePost(postService.resolveIdByUuid(uuid), authentication.getName());
        } catch (IllegalArgumentException ignored) {
            // 본인 소유가 아니거나 이미 삭제된 글 - 조용히 목록으로 돌아간다.
        }
        return redirect("posts", null, keyword, page);
    }

    @PostMapping("/comments/{id}/delete")
    public String deleteComment(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(required = false) String keyword,
                                 Authentication authentication) {
        try {
            postCommentService.deleteComment(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return redirect("comments", null, keyword, page);
    }

    @PostMapping("/schedule/{id}/delete")
    public String deleteScheduleComment(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(required = false) String keyword,
                                         Authentication authentication) {
        try {
            scheduleCommentService.deleteComment(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return redirect("schedule", null, keyword, page);
    }

    @PostMapping("/bookmarks/{uuid}/remove")
    public String removeBookmark(@PathVariable String uuid, @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(required = false) String keyword,
                                  Authentication authentication) {
        try {
            postService.removeBookmark(postService.resolveIdByUuid(uuid), authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return redirect("bookmarks", "post", keyword, page);
    }

    @PostMapping("/bookmarks/schedule/{id}/remove")
    public String removeScheduleBookmark(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(required = false) String keyword,
                                          Authentication authentication) {
        try {
            scheduleCommentService.removeBookmark(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return redirect("bookmarks", "schedule", keyword, page);
    }

    @PostMapping("/likes/{uuid}/remove")
    public String removeLike(@PathVariable String uuid, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(required = false) String keyword,
                              Authentication authentication) {
        try {
            postService.removeLike(postService.resolveIdByUuid(uuid), authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return redirect("likes", "post", keyword, page);
    }

    @PostMapping("/likes/schedule/{id}/remove")
    public String removeScheduleLike(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(required = false) String keyword,
                                      Authentication authentication) {
        try {
            scheduleCommentService.removeLike(id, authentication.getName());
        } catch (IllegalArgumentException ignored) {
        }
        return redirect("likes", "schedule", keyword, page);
    }

    private String redirect(String tab, String type, String keyword, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/mypage/activity")
                .queryParam("tab", tab)
                .queryParam("page", page);
        if (type != null) {
            builder.queryParam("type", type);
        }
        if (keyword != null && !keyword.isBlank()) {
            builder.queryParam("keyword", keyword);
        }
        return "redirect:" + builder.build().encode().toUriString();
    }
}
