package com.webschool.webschool.notification.service;

import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.dto.NotificationDto;
import com.webschool.webschool.notification.repository.NotificationRepository;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 알림 생성/조회/읽음 처리를 담당하는 서비스. 다른 도메인 서비스(PostService, PostCommentService,
// AdminPostService, AdminUserService)가 이벤트가 발생할 때마다 notify()/notifyIfNotSelf()를 호출해서
// 알림을 쌓는 방식 - 실시간(WebSocket) 대신 네비바에서 주기적으로 /notifications/unread-count를
// 폴링하는 방식으로 "실시간처럼" 보이게 한다(CLAUDE.md 로드맵 5번 항목의 "실시간 또는 폴링" 중 폴링 선택 -
// 이 프로젝트 규모에 WebSocket 인프라를 새로 들이는 건 과하다고 판단).
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notify(User recipient, Notification.Type type, String message, String link) {
        if (recipient == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setLink(link);
        notificationRepository.save(notification);
    }

    // 행위자 본인이 알림 대상과 같으면 보내지 않는다 (예: 본인 글에 본인이 댓글/좋아요를 남긴 경우)
    @Transactional
    public void notifyIfNotSelf(User recipient, String actorUsername, Notification.Type type,
                                 String message, String link) {
        if (recipient == null || (actorUsername != null && recipient.getUsername().equals(actorUsername))) {
            return;
        }
        notify(recipient, type, message, link);
    }

    // 공지사항(NOTICE) 게시물 작성 시 탈퇴하지 않은 전체 사용자(작성자 본인 제외)에게 broadcast
    @Transactional
    public void broadcastAnnouncement(String message, String link, String authorUsername) {
        List<User> targets = userRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> !u.isDeleted())
                .filter(u -> authorUsername == null || !u.getUsername().equals(authorUsername))
                .collect(Collectors.toList());

        List<Notification> batch = targets.stream().map(u -> {
            Notification n = new Notification();
            n.setRecipient(u);
            n.setType(Notification.Type.ANNOUNCEMENT);
            n.setMessage(message);
            n.setLink(link);
            return n;
        }).collect(Collectors.toList());

        notificationRepository.saveAll(batch);
    }

    public Page<NotificationDto> getPage(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        return notificationRepository
                .findByRecipient_IdOrderByCreatedAtDesc(user.getId(), PageRequest.of(Math.max(page, 0), size))
                .map(this::toDto);
    }

    public long getUnreadCount(String username) {
        if (username == null) {
            return 0;
        }
        return userRepository.findByUsername(username)
                .map(u -> notificationRepository.countByRecipient_IdAndReadFalse(u.getId()))
                .orElse(0L);
    }

    // 알림을 읽음 처리하고 이동할 링크를 반환 - 컨트롤러가 이 값으로 리다이렉트한다
    @Transactional
    public String markRead(Long id, String username) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getRecipient().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인의 알림만 확인할 수 있습니다.");
        }

        notification.setRead(true);
        return notification.getLink();
    }

    // 버그수정 프롬포트 요청 - "모두 읽음 처리"만 있고 개별 삭제가 없어서 알림함이 계속 쌓이기만
    // 했다. markRead()와 동일한 본인 확인 패턴.
    @Transactional
    public void delete(Long id, String username) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getRecipient().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인의 알림만 삭제할 수 있습니다.");
        }

        notificationRepository.delete(notification);
    }

    @Transactional
    public void markAllRead(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        notificationRepository.findByRecipient_IdAndReadFalse(user.getId())
                .forEach(n -> n.setRead(true));
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType().name())
                .typeLabel(n.getType().getLabel())
                .message(n.getMessage())
                .link(n.getLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
