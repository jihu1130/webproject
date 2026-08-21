package com.webschool.webschool.notice.service;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.notice.domain.Notice;
import com.webschool.webschool.notice.dto.NoticeDto;
import com.webschool.webschool.notice.repository.NoticeRepository;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 공지사항 - Post와 완전히 분리된 별도 모델(todo.md "공지사항(Notice) 기능 재설계" 요구사항).
// 이전에 있던 Post.Category.NOTICE(게시글 카테고리 방식) 공지 기능은 이 기능으로 완전히 대체됐다.
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 4000;

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 새 공지 작성 - 이전 활성 공지가 있으면 먼저 비활성화(보관)하고, 새 공지를 활성 상태로 만든다.
    // 작성 권한은 총관리자이거나 canManageNotices 권한을 받은 부관리자만 - 컨트롤러/인터셉터가 이미
    // 막아주지만(AdminAccessInterceptor의 "/admin/notices" 분기), 다른 관리자 기능과 동일하게 서비스
    // 단에서도 한 번 더 방어적으로 검증한다.
    // synchronized - 관리자 두 명이 거의 동시에 새 공지를 작성하면 둘 다 "활성 공지 없음/이전 공지"를
    // 동시에 읽어버려 활성 공지가 2개가 될 수 있다(그러면 findByActiveTrueAndDeletedFalse()가 1개만
    // 기대하는 다른 조회에서 예외를 던져 공지 배너가 깨짐). 이 앱은 단일 JVM 인스턴스로만 운영되고
    // 공지 작성 빈도도 낮은 규모라, DB 유니크 인덱스/비관적 락보다 이 방식이 훨씬 단순하고 확실하다.
    @Transactional
    public synchronized void createNotice(String username, String title, String content) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        if (!author.isSuperAdmin() && !author.isCanManageNotices()) {
            throw new IllegalArgumentException("공지사항 작성 권한이 없습니다.");
        }

        String validTitle = validateTitle(title);
        String validContent = validateContent(content);

        noticeRepository.findByActiveTrueAndDeletedFalse().ifPresent(prev -> prev.setActive(false));

        Notice notice = new Notice();
        notice.setTitle(validTitle);
        notice.setContent(validContent);
        notice.setAuthor(author);
        notice.setActive(true);
        noticeRepository.save(notice);

        notificationService.broadcastAnnouncement("[공지] " + validTitle, "/notices", username);
    }

    // 사용자 화면 고정 노출용 - 활성 공지가 하나도 없으면 빈 값
    public Optional<NoticeDto> getActiveNotice() {
        return noticeRepository.findByActiveTrueAndDeletedFalse().map(this::toDto);
    }

    // 관리자 이력 조회 + 공개 "공지" 탭 목록에서 공용으로 사용(최신순). keyword가 있으면 제목/내용에서
    // 찾는다 - 수정사항.md 지적("공지사항 목록에 검색 기능이 없음", 공지 개수 규모가 작다는 전제로
    // 다른 관리자 목록 화면들과 동일하게 메모리에서 필터링).
    public Page<NoticeDto> getHistory(int page, int size, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        List<NoticeDto> all = noticeRepository.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(n -> normalizedKeyword.isEmpty()
                        || n.getTitle().toLowerCase().contains(normalizedKeyword)
                        || n.getContent().toLowerCase().contains(normalizedKeyword))
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(all, page, size);
    }

    public NoticeDto getDetail(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        if (notice.isDeleted()) {
            throw new IllegalArgumentException("공지사항을 찾을 수 없습니다.");
        }
        return toDto(notice);
    }

    // 공지 수정 - todo.md 요구사항("오타 하나 나도 못 고치고, 고치려면 새 공지를 다시 올려야 하는데
    // 그럼 전체 유저에게 알림이 또 간다"). 활성/보관 상태와 무관하게 아무 공지나 수정할 수 있고,
    // createNotice()와 달리 재알림은 보내지 않는다(단순 오타 수정에도 전체 알림이 또 가면 스팸처럼
    // 느껴짐 - 실질적으로 새 소식이 아니라 기존 공지의 정정이므로).
    @Transactional
    public void updateNotice(Long id, String username, String title, String content) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        if (!actor.isSuperAdmin() && !actor.isCanManageNotices()) {
            throw new IllegalArgumentException("공지사항 수정 권한이 없습니다.");
        }

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        if (notice.isDeleted()) {
            throw new IllegalArgumentException("공지사항을 찾을 수 없습니다.");
        }

        notice.setTitle(validateTitle(title));
        notice.setContent(validateContent(content));
        notice.setUpdatedAt(LocalDateTime.now());
    }

    // 공지 삭제 - Post/PostComment와 동일한 소프트 삭제 패턴(물리적으로 지우지 않음). 활성 공지를
    // 삭제하면 활성 공지가 0개인 상태가 되는데, getActiveNotice()가 이미 Optional이라 그 경우
    // 사용자 화면에 배너가 그냥 안 뜰 뿐 에러로 이어지지 않는다.
    @Transactional
    public void deleteNotice(Long id, String username) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        if (!actor.isSuperAdmin() && !actor.isCanManageNotices()) {
            throw new IllegalArgumentException("공지사항 삭제 권한이 없습니다.");
        }

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        notice.setDeleted(true);
        notice.setDeletedAt(LocalDateTime.now());
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + MAX_TITLE_LENGTH + "자 이내로 입력해주세요.");
        }
        String trimmed = title.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("내용은 " + MAX_CONTENT_LENGTH + "자 이내로 입력해주세요.");
        }
        String trimmed = content.trim();
        BannedWordFilter.validate(trimmed);
        return trimmed;
    }

    private NoticeDto toDto(Notice n) {
        return NoticeDto.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .authorNickname(n.getAuthor().getNickname())
                .active(n.isActive())
                .createdAt(n.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
