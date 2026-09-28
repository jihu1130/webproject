package com.webschool.webschool.user.admin.service;

import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.user.admin.dto.AdminPointLogDto;
import com.webschool.webschool.user.domain.UserPointLog;
import com.webschool.webschool.user.repository.UserPointLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

// 포인트 관리(관리자 페이지 재구성, 2026-09-21 추가) - 사이트 전체 포인트 로그 감사 화면.
// AdminActionLogService.getLogs()와 동일한 관례(전체를 메모리에서 최신순 필터링 후 PageUtils로
// 페이지네이션 - 데이터 규모가 작다고 가정하는 이 프로젝트 관리자 화면 공통 패턴).
@Service
@RequiredArgsConstructor
public class AdminPointService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final int PAGE_SIZE = 20;

    private final UserPointLogRepository userPointLogRepository;

    public Page<AdminPointLogDto> getLogs(int page, String keyword) {
        List<AdminPointLogDto> filtered = userPointLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .filter(dto -> matches(keyword, dto.getUsername(), dto.getNickname(), dto.getReason()))
                .collect(Collectors.toList());
        return PageUtils.paginate(filtered, page, PAGE_SIZE);
    }

    private boolean matches(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase();
        for (String field : fields) {
            if (field != null && field.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private AdminPointLogDto toDto(UserPointLog log) {
        return AdminPointLogDto.builder()
                .userId(log.getUser().getId())
                .username(log.getUser().getUsername())
                .nickname(log.getUser().getNickname())
                .points(log.getPoints())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
