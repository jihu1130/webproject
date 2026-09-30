package com.webschool.webschool.global.embed;

import com.webschool.webschool.global.error.BusinessException;
import com.webschool.webschool.global.security.AuthenticationUtils;
import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.post.service.PostService;
import com.webschool.webschool.school.comment.domain.ScheduleComment;
import com.webschool.webschool.school.comment.repository.ScheduleCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 리치 에디터(게시글/오늘의 한마디 작성 화면)에서 "다른 게시물/한마디로 바로가기" 카드를 삽입할 때
// 쓰는 조회 전용 엔드포인트 - 사용자가 붙여넣은 URL이 실제로 존재하는 게시물/한마디를 가리키는지
// 확인하고, 카드에 보여줄 제목(또는 내용 미리보기)을 돌려준다. 저장은 하지 않는다 - 결과 title은
// rich-editor.js가 그대로 본문 HTML에 스냅샷으로 박아넣는다(대상이 나중에 수정/삭제돼도 카드 문구는
// 그대로 남는 단순한 방식 - 실시간 동기화는 이번 범위 밖).
@RestController
@RequestMapping("/api/embed")
@RequiredArgsConstructor
public class EmbedResolveController {

    private static final Pattern POST_PATTERN = Pattern.compile("/posts/([0-9a-fA-F-]{36})");
    private static final Pattern SCHEDULE_PATTERN = Pattern.compile("/school/comments/([0-9a-fA-F-]{36})");
    private static final int PREVIEW_LENGTH = 40;

    private final PostRepository postRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;
    private final PostService postService;

    @GetMapping("/resolve")
    @ResponseBody
    public ResponseEntity<?> resolve(@RequestParam String url, Authentication authentication) {
        Matcher postMatcher = POST_PATTERN.matcher(url);
        if (postMatcher.find()) {
            return resolvePost(postMatcher.group(1), authentication);
        }

        Matcher scheduleMatcher = SCHEDULE_PATTERN.matcher(url);
        if (scheduleMatcher.find()) {
            return resolveScheduleComment(scheduleMatcher.group(1), authentication);
        }

        return ResponseEntity.badRequest().body(Map.of("error", "게시물 또는 오늘의 한마디 링크만 삽입할 수 있어요."));
    }

    private ResponseEntity<?> resolvePost(String uuid, Authentication authentication) {
        Post post = postRepository.findByUuid(uuid).orElse(null);
        if (post == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "게시물을 찾을 수 없어요."));
        }
        // 상세 페이지를 못 여는 글(삭제/블라인드/비공개)은 카드도 못 만든다 - 이 엔드포인트는 제목을
        // 돌려주고, 카드가 본문에 스냅샷으로 박히면 그 글을 읽는 제3자에게도 제목이 그대로 노출된다.
        // 예전엔 PRIVATE만 막아서 블라인드된 글의 제목이 새어 나갔다(보안 점검 M2) - 상세 페이지와
        // 같은 판단(PostService.assertReadable)을 그대로 쓴다.
        try {
            postService.assertReadable(post, AuthenticationUtils.usernameOrNull(authentication));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "게시물을 찾을 수 없어요."));
        }
        return ResponseEntity.ok(Map.of(
                "type", "post",
                "label", "게시물",
                "url", "/posts/" + uuid,
                "title", post.getTitle()
        ));
    }

    private ResponseEntity<?> resolveScheduleComment(String uuid, Authentication authentication) {
        ScheduleComment comment = scheduleCommentRepository.findByUuid(uuid).orElse(null);
        if (comment == null || comment.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("error", "한마디를 찾을 수 없어요."));
        }
        // 블라인드된 한마디는 다른 화면에서 원문 대신 안내 문구로 가려지는데, 여기서 원문 미리보기를
        // 돌려주면 그 내용을 다른 글에 퍼뜨릴 수 있었다(보안 점검 M2) - 작성자 본인 외에는 막는다.
        String username = AuthenticationUtils.usernameOrNull(authentication);
        if (comment.isBlind() && (username == null || comment.getUser() == null
                || !comment.getUser().getUsername().equals(username))) {
            return ResponseEntity.badRequest().body(Map.of("error", "한마디를 찾을 수 없어요."));
        }
        String preview = HtmlSanitizer.toPlainText(comment.getContent());
        if (preview.length() > PREVIEW_LENGTH) {
            preview = preview.substring(0, PREVIEW_LENGTH) + "...";
        } else if (preview.isBlank()) {
            preview = "(사진/동영상)";
        }
        return ResponseEntity.ok(Map.of(
                "type", "schedule",
                "label", "오늘의 한마디",
                "url", "/school/comments/" + uuid,
                "title", preview
        ));
    }
}
