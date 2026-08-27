package com.webschool.webschool.user.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.domain.UserPenalty;
import com.webschool.webschool.user.dto.UserPenaltyDto;
import com.webschool.webschool.user.repository.UserPenaltyRepository;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 관리자 계정 제재(경고/게시글 작성정지/커뮤니티 임시차단/계정 비활성화) 관리. User에는 정지 상태를
// 나타내는 필드를 두지 않는다 - 매 요청마다 UserPenalty 이력에서 "지금 시점 기준 유효한 제재가
// 있는지"를 조회해서 판단한다(SchoolService의 방학 D-Day와 동일한 "읽는 시점에 계산" 방식). 그래서
// 기간이 지나면 별도의 만료 처리/재활성화 로직 없이 자동으로 "제재 아님"이 된다. 기존 User.active
// (총관리자가 즉시 켜고 끄는 무기한 수동 정지)는 이 서비스와 무관하게 그대로 유지된다.
@Service
@RequiredArgsConstructor
public class UserPenaltyService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final UserPenaltyRepository userPenaltyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminActionLogService adminActionLogService;
    private final UserPointService userPointService;

    public List<UserPenaltyDto> getHistory(Long targetId) {
        return userPenaltyRepository.findByTarget_IdOrderByIssuedAtDesc(targetId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // durationDays가 null이면 영구 제재
    @Transactional
    public void issue(Long targetId, UserPenalty.Type type, String reason, Integer durationDays,
                       String actingAdminUsername) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        User issuer = userRepository.findByUsername(actingAdminUsername)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));

        if (target.getUsername().equals(actingAdminUsername)) {
            throw new IllegalArgumentException("본인에게는 제재를 부여할 수 없습니다.");
        }
        if (target.isSuperAdmin()) {
            throw new IllegalArgumentException("총관리자 계정은 제재할 수 없습니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("제재 사유를 입력해주세요.");
        }

        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 300) {
            trimmedReason = trimmedReason.substring(0, 300);
        }

        UserPenalty penalty = new UserPenalty();
        penalty.setTarget(target);
        penalty.setIssuedBy(issuer);
        penalty.setType(type);
        penalty.setReason(trimmedReason);
        penalty.setExpiresAt(durationDays != null && durationDays > 0
                ? LocalDateTime.now().plusDays(durationDays) : null);
        userPenaltyRepository.save(penalty);
        userPointService.deductForPenalty(target, type.getPointPenalty(), "제재: " + type.getLabel());

        String periodText = penalty.getExpiresAt() != null ? durationDays + "일간" : "무기한";
        notificationService.notify(target, Notification.Type.ACCOUNT,
                "[" + type.getLabel() + "] " + periodText + " 제재가 부여되었습니다. 포인트 "
                        + type.getPointPenalty() + "점이 차감되었습니다. 사유: " + trimmedReason, null);
        adminActionLogService.log("USER", target.getId(), "PENALTY_ISSUE",
                type.getLabel() + " " + periodText + " (" + truncate(trimmedReason) + ")");
    }

    @Transactional
    public void revoke(Long penaltyId) {
        UserPenalty penalty = userPenaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new IllegalArgumentException("제재 기록을 찾을 수 없습니다."));

        if (penalty.isRevoked()) {
            throw new IllegalArgumentException("이미 해제된 제재입니다.");
        }

        penalty.setRevoked(true);
        penalty.setRevokedAt(LocalDateTime.now());
        notificationService.notify(penalty.getTarget(), Notification.Type.ACCOUNT,
                "[" + penalty.getType().getLabel() + "] 제재가 조기 해제되었습니다.", null);
        adminActionLogService.log("USER", penalty.getTarget().getId(), "PENALTY_REVOKE", penalty.getType().getLabel());
    }

    // 로그인 가능 여부 판단(CustomUserDetailsService)에 쓰인다.
    public boolean isDeactivated(Long userId) {
        return !userPenaltyRepository.findActive(userId, UserPenalty.Type.DEACTIVATION, LocalDateTime.now()).isEmpty();
    }

    // 커뮤니티 임시차단은 게시글 작성정지보다 넓은 제재라 게시글 작성도 함께 막는다.
    public void assertCanCreatePost(User user) {
        assertNotSuspended(user, UserPenalty.Type.COMMUNITY_SUSPENSION, "커뮤니티 활동");
        assertNotSuspended(user, UserPenalty.Type.POST_SUSPENSION, "게시글 작성");
    }

    public void assertCanComment(User user) {
        assertNotSuspended(user, UserPenalty.Type.COMMUNITY_SUSPENSION, "커뮤니티 활동");
    }

    private void assertNotSuspended(User user, UserPenalty.Type type, String actionLabel) {
        List<UserPenalty> active = userPenaltyRepository.findActive(user.getId(), type, LocalDateTime.now());
        if (active.isEmpty()) {
            return;
        }
        UserPenalty p = active.get(0);
        String until = p.getExpiresAt() != null ? p.getExpiresAt().format(DISPLAY_FORMAT) + "까지" : "무기한";
        throw new IllegalArgumentException(actionLabel + " 제재 중이라 이용할 수 없습니다(" + until + "). 사유: " + p.getReason());
    }

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private UserPenaltyDto toDto(UserPenalty p) {
        return UserPenaltyDto.builder()
                .id(p.getId())
                .type(p.getType().name())
                .typeLabel(p.getType().getLabel())
                .reason(p.getReason())
                .issuedByNickname(p.getIssuedBy().getNickname())
                .issuedAt(p.getIssuedAt().format(DISPLAY_FORMAT))
                .expiresAt(p.getExpiresAt() != null ? p.getExpiresAt().format(DISPLAY_FORMAT) : null)
                .revoked(p.isRevoked())
                .active(p.isCurrentlyActive())
                .build();
    }
}
