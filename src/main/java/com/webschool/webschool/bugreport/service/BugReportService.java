package com.webschool.webschool.bugreport.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.bugreport.domain.BugReport;
import com.webschool.webschool.bugreport.dto.BugReportDto;
import com.webschool.webschool.bugreport.repository.BugReportRepository;
import com.webschool.webschool.global.util.PageUtils;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 버그 리포트 - 비로그인 사용자도 제출 가능(사용자 확정 정책). 관리 화면은 총관리자 전용
// (AdminAccessInterceptor의 "/admin/bug-reports" 분기 참고, /admin/users·/admin/audit-log와 동일 패턴).
@Service
@RequiredArgsConstructor
public class BugReportService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final BugReportRepository bugReportRepository;
    private final UserRepository userRepository;
    private final AdminActionLogService adminActionLogService;

    // username이 null이면 비로그인 제출(SecurityConfig가 GET/POST /bug-reports/new를 permitAll로 열어둠).
    @Transactional
    public void submitReport(String username, String title, String content,
                              String reporterNickname, String contactEmail) {
        String validTitle = validateTitle(title);
        String validContent = validateContent(content);

        BugReport report = new BugReport();
        report.setTitle(validTitle);
        report.setContent(validContent);

        if (username != null) {
            User reporter = userRepository.findByUsername(username).orElse(null);
            report.setReporter(reporter);
        } else {
            String trimmedNickname = reporterNickname == null ? "" : reporterNickname.trim();
            report.setReporterNickname(trimmedNickname.isBlank() ? null : trimmedNickname);
        }

        if (contactEmail != null && !contactEmail.isBlank()) {
            String trimmedEmail = contactEmail.trim();
            if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
                throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
            }
            report.setContactEmail(trimmedEmail);
        }

        bugReportRepository.save(report);
    }

    public Page<BugReportDto> getList(int page, int size) {
        List<BugReportDto> all = bugReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(all, page, size);
    }

    @Transactional
    public void resolve(Long id, String actorUsername) {
        requireSuperAdmin(actorUsername);
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("버그 리포트를 찾을 수 없습니다."));
        report.setResolved(!report.isResolved());
        report.setResolvedAt(report.isResolved() ? LocalDateTime.now() : null);
        adminActionLogService.log("BUG_REPORT", report.getId(),
                report.isResolved() ? "RESOLVE" : "REOPEN", truncate(report.getTitle()));
    }

    @Transactional
    public void delete(Long id, String actorUsername) {
        requireSuperAdmin(actorUsername);
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("버그 리포트를 찾을 수 없습니다."));
        adminActionLogService.log("BUG_REPORT", report.getId(), "DELETE", truncate(report.getTitle()));
        bugReportRepository.delete(report);
    }

    private void requireSuperAdmin(String actorUsername) {
        User actor = userRepository.findByUsername(actorUsername)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        if (!actor.isSuperAdmin()) {
            throw new IllegalArgumentException("버그 리포트 관리 권한이 없습니다.");
        }
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

    private String truncate(String text) {
        int limit = 40;
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private BugReportDto toDto(BugReport r) {
        String display;
        if (r.getReporter() != null) {
            display = r.getReporter().getNickname();
        } else if (r.getReporterNickname() != null) {
            display = r.getReporterNickname();
        } else {
            display = "익명";
        }
        return BugReportDto.builder()
                .id(r.getId())
                .title(r.getTitle())
                .content(r.getContent())
                .reporterDisplay(display)
                .contactEmail(r.getContactEmail())
                .resolved(r.isResolved())
                .createdAt(r.getCreatedAt().format(DISPLAY_FORMAT))
                .build();
    }
}
