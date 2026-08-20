package com.webschool.webschool.admin.service;

import com.webschool.webschool.admin.domain.AdminActionLog;
import com.webschool.webschool.admin.dto.AdminActionLogDto;
import com.webschool.webschool.admin.repository.AdminActionLogRepository;
import com.webschool.webschool.global.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 관리자 조치 감사 로그 - "누가 언제 무엇을 했는지" 기록만 남기는 append-only 서비스. 어떤 신고/버그
// 감사에서 "관리자 액션 이력이 아예 없다"고 지적된 갭을 메우기 위해 신설했다. AdminPostService/
// AdminScheduleCommentService/AdminUserService의 블라인드·해제·삭제·복구·승격·강등·정지·해제
// 메서드마다 이 서비스의 log()를 한 줄씩 호출한다.
@Service
@RequiredArgsConstructor
public class AdminActionLogService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int PAGE_SIZE = 20;

    private static final Map<String, String> TARGET_TYPE_LABELS = Map.of(
            "POST", "게시글",
            "COMMENT", "댓글",
            "SCHEDULE_COMMENT", "한마디",
            "USER", "계정"
    );

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("BLIND", "블라인드"),
            Map.entry("UNBLIND", "블라인드 해제"),
            Map.entry("REPORT_CLEAR", "문제없음 처리"),
            Map.entry("REPORT_UNCLEAR", "문제없음 철회"),
            Map.entry("DELETE", "삭제"),
            Map.entry("RESTORE", "복구"),
            Map.entry("PROMOTE", "승격"),
            Map.entry("DEMOTE", "강등"),
            Map.entry("PERMISSIONS", "권한 변경"),
            Map.entry("DEACTIVATE", "정지"),
            Map.entry("ACTIVATE", "정지 해제")
    );

    private final AdminActionLogRepository adminActionLogRepository;

    // 현재 요청을 처리 중인 관리자 계정을 SecurityContext에서 직접 읽는다 - 호출부(AdminPostService
    // 등의 블라인드/삭제 메서드)마다 관리자 username 파라미터를 새로 추가할 필요 없이 기존 메서드
    // 시그니처를 그대로 둔 채 로그인 한 줄만 끼워 넣을 수 있게 하기 위함(요청 스레드 안에서 호출되는
    // 한 SecurityContextHolder는 항상 현재 로그인한 관리자를 가리킨다).
    @Transactional
    public void log(String targetType, Long targetId, String action, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String adminUsername = authentication != null ? authentication.getName() : "system";

        AdminActionLog entry = new AdminActionLog();
        entry.setAdminUsername(adminUsername);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setAction(action);
        entry.setDetail(detail);
        adminActionLogRepository.save(entry);
    }

    public Page<AdminActionLogDto> getLogs(int page, String adminUsername, String targetType,
                                            LocalDate from, LocalDate to) {
        List<AdminActionLogDto> filtered = adminActionLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(l -> adminUsername == null || adminUsername.isBlank()
                        || l.getAdminUsername().equalsIgnoreCase(adminUsername))
                .filter(l -> targetType == null || targetType.isBlank() || l.getTargetType().equals(targetType))
                .filter(l -> matchesDateRange(l.getCreatedAt(), from, to))
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    private boolean matchesDateRange(LocalDateTime createdAt, LocalDate from, LocalDate to) {
        if (createdAt == null) {
            return true;
        }
        LocalDate date = createdAt.toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    private AdminActionLogDto toDto(AdminActionLog l) {
        return AdminActionLogDto.builder()
                .id(l.getId())
                .adminUsername(l.getAdminUsername())
                .targetType(l.getTargetType())
                .targetTypeLabel(TARGET_TYPE_LABELS.getOrDefault(l.getTargetType(), l.getTargetType()))
                .targetId(l.getTargetId())
                .action(l.getAction())
                .actionLabel(ACTION_LABELS.getOrDefault(l.getAction(), l.getAction()))
                .detail(l.getDetail())
                .createdAt(l.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
