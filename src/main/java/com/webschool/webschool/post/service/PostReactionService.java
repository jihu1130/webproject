package com.webschool.webschool.post.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.domain.Post;
import com.webschool.webschool.post.domain.PostBookmark;
import com.webschool.webschool.post.domain.PostLike;
import com.webschool.webschool.post.repository.PostBookmarkRepository;
import com.webschool.webschool.post.repository.PostLikeRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import com.webschool.webschool.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// 게시글 좋아요/북마크(토글 + 마이페이지 전용 "제거"). 댓글 쪽은 CommentReactionService에 같은
// 모양으로 있다. 2026-09-28 PostService에서 분리.
@Service
@RequiredArgsConstructor
public class PostReactionService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserPointService userPointService;

    // 좋아요 토글 - 이미 눌렀으면 취소, 안 눌렀으면 추가. PostLike 유니크 제약(post_id, user_id)
    // 덕분에 같은 사람이 두 번 좋아요를 쌓을 수 없다. 카운트는 Post.likeCount에 비정규화해서
    // 목록/상세 조회 때마다 COUNT 쿼리를 따로 안 날려도 되게 한다.
    @Transactional
    public Map<String, Object> toggleLike(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        var existing = postLikeRepository.findByPost_IdAndUser_Id(id, user.getId());
        boolean liked;
        int displayLikeCount;
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            postRepository.decrementLikeCount(id);
            displayLikeCount = Math.max(0, post.getLikeCount() - 1);
            liked = false;
        } else {
            PostLike like = new PostLike();
            like.setPost(post);
            like.setUser(user);
            postLikeRepository.save(like);
            postRepository.incrementLikeCount(id);
            displayLikeCount = post.getLikeCount() + 1;
            liked = true;
            notificationService.notifyIfNotSelf(post.getAuthor(), username, Notification.Type.LIKE,
                    user.getNickname() + "님이 회원님의 글 '" + truncate(post.getTitle()) + "'을(를) 좋아합니다.",
                    "/posts/" + post.getUuid());
            userPointService.award(post.getAuthor(), UserPointService.LIKE_RECEIVED, "게시글 좋아요 받음");
        }
        return Map.of("liked", liked, "likeCount", displayLikeCount);
    }

    // 북마크 토글 - 좋아요와 동일한 패턴이지만 카운트를 공개하지 않는 개인용 기능이라 PostBookmark
    // 자체가 마이페이지 "북마크" 탭의 조회 근거가 된다(비정규화 카운트 없음).
    @Transactional
    public boolean toggleBookmark(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        var existing = postBookmarkRepository.findByPost_IdAndUser_Id(id, user.getId());
        if (existing.isPresent()) {
            postBookmarkRepository.delete(existing.get());
            return false;
        }
        PostBookmark bookmark = new PostBookmark();
        bookmark.setPost(post);
        bookmark.setUser(user);
        postBookmarkRepository.save(bookmark);
        return true;
    }

    // 마이페이지 "북마크" 탭의 "해제" 버튼 전용 - toggleBookmark()와 달리 항상 "제거"만 하는
    // 멱등 동작이다(토글을 재사용하면 이미 해제된 상태에서 다시 누를 때 오히려 북마크가 켜지는
    // 사고가 날 수 있어 분리함).
    @Transactional
    public void removeBookmark(Long id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        postBookmarkRepository.findByPost_IdAndUser_Id(id, user.getId())
                .ifPresent(postBookmarkRepository::delete);
    }

    // 마이페이지 "좋아요" 탭의 "취소" 버튼 전용 - removeBookmark()와 동일한 이유로 토글이 아닌
    // 항상 "제거"만 하는 멱등 동작으로 분리.
    @Transactional
    public void removeLike(Long id, String username) {
        postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        postLikeRepository.findByPost_IdAndUser_Id(id, user.getId()).ifPresent(like -> {
            postLikeRepository.delete(like);
            postRepository.decrementLikeCount(id);
        });
    }

    // 알림 메시지에 제목을 넣을 때 너무 길어지지 않도록 자르는 용도 (Notification.message는 200자 제한)
    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
