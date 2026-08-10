package com.webschool.webschool.post.controller;

import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.dto.PostDetailDto;
import com.webschool.webschool.post.dto.PostFormDto;
import com.webschool.webschool.post.dto.PostListItemDto;
import com.webschool.webschool.post.dto.PostReportResultDto;
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

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String keyword,
                        Model model) {
        Post.Category categoryFilter = parseCategoryFilter(category);
        Page<PostListItemDto> posts = postService.getList(page, categoryFilter, keyword);
        model.addAttribute("posts", posts);
        model.addAttribute("selectedCategory", categoryFilter != null ? categoryFilter.name() : null);
        model.addAttribute("keyword", keyword);
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
    public String detail(@PathVariable String uuid, Authentication authentication, HttpSession session, Model model) {
        try {
            Long id = postService.resolveIdByUuid(uuid);
            boolean countView = shouldCountView(session, id);
            PostDetailDto post = postService.getDetail(id, extractUsername(authentication), countView);
            model.addAttribute("post", post);
            model.addAttribute("images", postImageService.getImages(id));
            return "post/detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/posts";
        }
    }

    @GetMapping("/{uuid}/edit")
    public String editForm(@PathVariable String uuid, Authentication authentication, Model model) {
        try {
            Long id = postService.resolveIdByUuid(uuid);
            PostFormDto form = postService.getForEdit(id, authentication.getName());
            model.addAttribute("postForm", form);
            model.addAttribute("mode", "edit");
            model.addAttribute("postUuid", uuid);
            model.addAttribute("existingImages", postImageService.getImages(id));
            return "post/form";
        } catch (IllegalArgumentException e) {
            return "redirect:/posts/" + uuid;
        }
    }

    @PostMapping("/{uuid}/edit")
    public String update(@PathVariable String uuid, @ModelAttribute("postForm") PostFormDto postForm,
                          @RequestParam(value = "images", required = false) List<MultipartFile> images,
                          @RequestParam(value = "removeImageIds", required = false) List<Long> removeImageIds,
                          Authentication authentication, Model model) {
        Long id;
        try {
            id = postService.resolveIdByUuid(uuid);
        } catch (IllegalArgumentException e) {
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
    public String delete(@PathVariable String uuid, Authentication authentication) {
        try {
            Long id = postService.resolveIdByUuid(uuid);
            // 소프트 딜리트라서 첨부 이미지는 지우지 않고 그대로 보존한다(6-6 항목 참고).
            postService.deletePost(id, authentication.getName());
        } catch (IllegalArgumentException e) {
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
