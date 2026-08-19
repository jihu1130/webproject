package com.webschool.webschool.global.embed;

import com.webschool.webschool.global.util.HtmlSanitizer;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.domain.ScheduleComment;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private static final Pattern SCHEDULE_PATTERN = Pattern.compile("/school/comments/(\\d+)");
    private static final int PREVIEW_LENGTH = 40;

    private final PostRepository postRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;

    @GetMapping("/resolve")
    @ResponseBody
    public ResponseEntity<?> resolve(@RequestParam String url) {
        Matcher postMatcher = POST_PATTERN.matcher(url);
        if (postMatcher.find()) {
            return resolvePost(postMatcher.group(1));
        }

        Matcher scheduleMatcher = SCHEDULE_PATTERN.matcher(url);
        if (scheduleMatcher.find()) {
            return resolveScheduleComment(Long.parseLong(scheduleMatcher.group(1)));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "게시물 또는 오늘의 한마디 링크만 삽입할 수 있어요."));
    }

    private ResponseEntity<?> resolvePost(String uuid) {
        Post post = postRepository.findByUuid(uuid).orElse(null);
        if (post == null || post.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("error", "게시물을 찾을 수 없어요."));
        }
        return ResponseEntity.ok(Map.of(
                "type", "post",
                "label", "게시물",
                "url", "/posts/" + uuid,
                "title", post.getTitle()
        ));
    }

    private ResponseEntity<?> resolveScheduleComment(Long id) {
        ScheduleComment comment = scheduleCommentRepository.findById(id).orElse(null);
        if (comment == null || comment.isDeleted()) {
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
                "url", "/school/comments/" + id,
                "title", preview
        ));
    }
}
