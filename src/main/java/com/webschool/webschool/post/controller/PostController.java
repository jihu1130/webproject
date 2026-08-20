package com.webschool.webschool.post.controller;

import com.webschool.webschool.notice.service.NoticeService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.dto.PostDetailDto;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.dto.PostListItemDto;
import com.webschool.webschool.post.dto.PostReportResultDto;
import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.post.service.PostImageService;
import com.webschool.webschool.post.service.PostService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private static final String VIEWED_POSTS_SESSION_KEY = "viewedPostIds";

    private final PostService postService;
    private final PostImageService postImageService;
    private final NoticeService noticeService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) Integer size,
                        @RequestParam(required = false) String scope,
                        @RequestParam(required = false) String sort,
                        Model model) {
        Post.Category categoryFilter = parseCategoryFilter(category);
        int pageSize = PageUtils.normalizeSize(size);
        Page<PostListItemDto> posts = postService.getList(page, categoryFilter, keyword, pageSize, scope, sort);
        model.addAttribute("posts", posts);
        model.addAttribute("selectedCategory", categoryFilter != null ? categoryFilter.name() : null);
        model.addAttribute("keyword", keyword);
        model.addAttribute("scope", scope == null ? "" : scope);
        model.addAttribute("sort", sort == null ? "" : sort);

        // 활성 공지 배너는 "전체" 탭 첫 페이지, 검색어 없을 때만 보여준다 - 예전 Post.Category.NOTICE
        // 고정 노출과 동일한 노출 조건(post/list.html 참고).
        if (categoryFilter == null && (keyword == null || keyword.isBlank()) && page == 0) {
            noticeService.getActiveNotice().ifPresent(notice -> model.addAttribute("activeNotice", notice));
        }

        return "post/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        PostFormDto form = new PostFormDto();
        form.setCategory(Post.Category.FREE.name());
        model.addAttribute("postForm", form);
        model.addAttribute("mode", "create");
        return "post/form";
    }

    @PostMapping
    public String create(@ModelAttribute("postForm") PostFormDto postForm,
                          @RequestParam(value = "images", required = false) List<MultipartFile> images,
                          Authentication authentication, Model model) {
        try {
            postImageService.validate(images);
            String uuid = postService.createPost(authentication.getName(), postForm);
            Long id = postService.resolveIdByUuid(uuid);
            postImageService.saveImages(id, authentication.getName(), images);
            return "redirect:/posts/" + uuid;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "create");
            return "post/form";
        }
    }

    @GetMapping("/{uuid}")
    public String detail(@PathVariable String uuid, Authentication authentication, HttpSession session, Model model,
                          RedirectAttributes redirectAttributes) {
        try {
            Long id = postService.resolveIdByUuid(uuid);
            boolean countView = shouldCountView(session, id);
            PostDetailDto post = postService.getDetail(id, extractUsername(authentication), countView);
            model.addAttribute("post", post);
            model.addAttribute("images", postImageService.getImages(id));
            return "post/detail";
        } catch (IllegalArgumentException e) {
            // 버그 수정: 예전엔 여기서 이유 없이 그냥 목록으로 튕겨나갔다(오래된 링크, 삭제된 글,
            // 블라인드된 글 등) - 왜 안 되는지 flash 메시지로 알려준다.
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/posts";
        }
    }

    @GetMapping("/{uuid}/edit")
    public String editForm(@PathVariable String uuid, Authentication authentication, Model model,
                            RedirectAttributes redirectAttributes) {
        try {
            Long id = postService.resolveIdByUuid(uuid);
            PostFormDto form = postService.getForEdit(id, authentication.getName());
            model.addAttribute("postForm", form);
            model.addAttribute("mode", "edit");
            model.addAttribute("postUuid", uuid);
            model.addAttribute("existingImages", postImageService.getImages(id));
            return "post/form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/posts/" + uuid;
        }
    }

    @PostMapping("/{uuid}/edit")
    public String update(@PathVariable String uuid, @ModelAttribute("postForm") PostFormDto postForm,
                          @RequestParam(value = "images", required = false) List<MultipartFile> images,
                          @RequestParam(value = "removeImageIds", required = false) List<Long> removeImageIds,
                          Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Long id;
        try {
            id = postService.resolveIdByUuid(uuid);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/posts";
        }

        try {
            postImageService.validate(images);
            postService.updatePost(id, authentication.getName(), postForm);
            postImageService.deleteImages(id, authentication.getName(), removeImageIds);
            postImageService.saveImages(id, authentication.getName(), images);
            return "redirect:/posts/" + uuid;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "edit");
            model.addAttribute("postUuid", uuid);
            model.addAttribute("existingImages", postImageService.getImages(id));
            return "post/form";
        }
    }

    @PostMapping("/{uuid}/delete")
    public String delete(@PathVariable String uuid, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            Long id = postService.resolveIdByUuid(uuid);
            // 소프트 딜리트라서 첨부 이미지는 지우지 않고 그대로 보존한다(6-6 항목 참고).
            postService.deletePost(id, authentication.getName());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/posts/" + uuid;
        }
        return "redirect:/posts";
    }

    @PostMapping("/{uuid}/report")
    @ResponseBody
    public Map<String, Object> report(@PathVariable String uuid,
                                       @RequestParam(required = false) String reason,
                                       Authentication authentication) {
        Long id = postService.resolveIdByUuid(uuid);
        PostReportResultDto result = postService.reportPost(id, authentication.getName(), reason);
        return Map.of("success", true, "reportCount", result.reportCount(), "blind", result.blind());
    }

    @PostMapping("/{uuid}/like")
    @ResponseBody
    public Map<String, Object> like(@PathVariable String uuid, Authentication authentication) {
        Long id = postService.resolveIdByUuid(uuid);
        return postService.toggleLike(id, authentication.getName());
    }

    @PostMapping("/{uuid}/bookmark")
    @ResponseBody
    public Map<String, Object> bookmark(@PathVariable String uuid, Authentication authentication) {
        Long id = postService.resolveIdByUuid(uuid);
        boolean bookmarked = postService.toggleBookmark(id, authentication.getName());
        return Map.of("bookmarked", bookmarked);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private Post.Category parseCategoryFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Post.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean shouldCountView(HttpSession session, Long id) {
        Set<Long> viewed = (Set<Long>) session.getAttribute(VIEWED_POSTS_SESSION_KEY);
        if (viewed == null) {
            viewed = new HashSet<>();
            session.setAttribute(VIEWED_POSTS_SESSION_KEY, viewed);
        }
        return viewed.add(id);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }
}
