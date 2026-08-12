package com.webschool.webschool.user.service;

import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserBlock;
import com.webschool.webschool.user.dto.UserBlockDto;
import com.webschool.webschool.user.repository.UserBlockRepository;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 사용자 간 차단 - 차단하면 상대방은 사이트 전체에서 내가 쓴 게시글/댓글에 댓글을 달 수 없다
// (양방향 확인: 둘 중 누가 차단했든 서로 못 단다). 익명 게시판(Post.Category.ANONYMOUS)은 완전히
// 제외한다 - 작성자 식별 자체가 서버 단에서 가려져 있어서(PostService.displayNickname 참고)
// "이 사람을 차단" UI를 노출할 방법이 없다. "오늘의 한마디"(ScheduleComment)도 특정 개인 소유가
// 아니라 학교/날짜/반별로 공유되는 게시판이라 차단 적용 대상에서 제외한다.
@Service
@RequiredArgsConstructor
public class UserBlockService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    public List<UserBlockDto> getMyBlocks(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        return userBlockRepository.findByBlocker_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // durationDays가 null이면 영구 차단. 이미 차단한 사람을 다시 차단하면 기간만 갱신한다.
    @Transactional
    public void block(String username, Long targetId, Integer durationDays) {
        User blocker = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("차단할 사용자를 찾을 수 없습니다."));

        if (blocker.getId().equals(target.getId())) {
            throw new IllegalArgumentException("본인은 차단할 수 없습니다.");
        }

        UserBlock block = userBlockRepository.findByBlocker_IdAndBlocked_Id(blocker.getId(), target.getId())
                .orElseGet(UserBlock::new);
        block.setBlocker(blocker);
        block.setBlocked(target);
        block.setExpiresAt(durationDays != null && durationDays > 0
                ? LocalDateTime.now().plusDays(durationDays) : null);
        userBlockRepository.save(block);
    }

    @Transactional
    public void unblock(String username, Long blockId) {
        UserBlock block = userBlockRepository.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("차단 기록을 찾을 수 없습니다."));

        if (!block.getBlocker().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인이 설정한 차단만 해제할 수 있습니다.");
        }

        userBlockRepository.delete(block);
    }

    // 댓글 작성 시점에 호출 - 글쓴이(postAuthor)와 댓글 작성자(commenter) 중 누구든 상대를 차단한
    // 적이 있으면 막는다. 호출 전에 익명 게시물인지는 호출부(PostCommentService)에서 걸러야 한다.
    public void assertNotBlocked(User commenter, User postAuthor) {
        if (commenter.getId().equals(postAuthor.getId())) {
            return;
        }
        if (userBlockRepository.existsActiveBetween(commenter.getId(), postAuthor.getId(), LocalDateTime.now())) {
            throw new IllegalArgumentException("차단 관계로 인해 댓글을 작성할 수 없습니다.");
        }
    }

    private UserBlockDto toDto(UserBlock b) {
        return UserBlockDto.builder()
                .id(b.getId())
                .userId(b.getBlocked().getId())
                .nickname(b.getBlocked().getNickname())
                .createdAt(b.getCreatedAt().format(DISPLAY_FORMAT))
                .expiresAt(b.getExpiresAt() != null ? b.getExpiresAt().format(DISPLAY_FORMAT) : null)
                .permanent(b.getExpiresAt() == null)
                .build();
    }
}
