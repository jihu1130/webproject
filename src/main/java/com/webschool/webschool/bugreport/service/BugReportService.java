package com.webschool.webschool.bugreport.service;

import com.webschool.webschool.admin.service.AdminActionLogService;
import com.webschool.webschool.bugreport.domain.BugReport;
import com.webschool.webschool.bugreport.domain.BugReportAttachment;
import com.webschool.webschool.bugreport.domain.InquiryReply;
import com.webschool.webschool.bugreport.dto.BugReportAttachmentDto;
import com.webschool.webschool.bugreport.dto.BugReportDto;
import com.webschool.webschool.bugreport.dto.InquiryReplyDto;
import com.webschool.webschool.bugreport.repository.BugReportAttachmentRepository;
import com.webschool.webschool.bugreport.repository.BugReportRepository;
import com.webschool.webschool.bugreport.repository.InquiryReplyRepository;
import com.webschool.webschool.global.mail.MailService;
import com.webschool.webschool.global.upload.FileUploadService;
import com.webschool.webschool.global.upload.UploadedFileDto;
import com.webschool.webschool.global.util.PageUtils;
import com.webschool.webschool.notification.domain.Notification;
import com.webschool.webschool.notification.service.NotificationService;
import com.webschool.webschool.post.util.BannedWordFilter;
import com.webschool.webschool.user.domain.User;
import com.webschool.webschool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private static final int MAX_ATTACHMENTS = 5;

    private final BugReportRepository bugReportRepository;
    private final BugReportAttachmentRepository bugReportAttachmentRepository;
    private final InquiryReplyRepository inquiryReplyRepository;
    private final UserRepository userRepository;
    private final AdminActionLogService adminActionLogService;
    private final FileUploadService fileUploadService;
    private final NotificationService notificationService;
    private final MailService mailService;

    // username이 null이면 비로그인 제출(SecurityConfig가 GET/POST /bug-reports/new를 permitAll로 열어둠).
    // 첨부는 리치 에디터 본문 삽입이 아니라 게시글 이미지처럼 별도 목록(사용자 요청) - 최대 5개,
    // 사진/영상만 허용(그 외 확장자는 FileUploadService.store()를 태우기 전에 걸러서 디스크에 쓰지 않는다).
    // 비로그인 제출은 답변을 받을 방법이 이메일뿐이라 contactEmail을 필수로 강제한다(로그인 제출은
    // 인앱 알림으로 받으므로 계속 선택 입력).
    @Transactional
    public void submitReport(String username, String categoryName, String title, String content,
                              String reporterNickname, String contactEmail, List<MultipartFile> files) {
        String validTitle = validateTitle(title);
        String validContent = validateContent(content);
        validateAttachments(files);
        BugReport.Category category = parseCategory(categoryName);

        BugReport report = new BugReport();
        report.setCategory(category);
        report.setTitle(validTitle);
        report.setContent(validContent);

        if (username != null) {
            User reporter = userRepository.findByUsername(username).orElse(null);
            report.setReporter(reporter);
        } else {
            if (contactEmail == null || contactEmail.isBlank()) {
                throw new IllegalArgumentException("비로그인 상태에서는 연락 가능한 이메일을 입력해주세요.");
            }
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

        BugReport saved = bugReportRepository.save(report);
        saveAttachments(saved, files);
    }

    private BugReport.Category parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return BugReport.Category.BUG;
        }
        try {
            return BugReport.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 문의 유형입니다.");
        }
    }

    private void validateAttachments(List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        long count = files.stream().filter(f -> f != null && !f.isEmpty()).count();
        if (count > MAX_ATTACHMENTS) {
            throw new IllegalArgumentException("첨부파일은 최대 " + MAX_ATTACHMENTS + "개까지 업로드할 수 있습니다.");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (!fileUploadService.isImageOrVideoExtension(file.getOriginalFilename())) {
                throw new IllegalArgumentException("사진 또는 영상 파일만 첨부할 수 있습니다.");
            }
        }
    }

    private void saveAttachments(BugReport report, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        int order = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            UploadedFileDto uploaded = fileUploadService.store(file);

            BugReportAttachment attachment = new BugReportAttachment();
            attachment.setBugReport(report);
            attachment.setUrl(uploaded.getUrl());
            attachment.setOriginalFilename(uploaded.getOriginalFilename());
            attachment.setKind(uploaded.getKind());
            attachment.setSortOrder(order++);
            bugReportAttachmentRepository.save(attachment);
        }
    }

    public Page<BugReportDto> getList(int page, int size) {
        return getList(null, null, null, page, size);
    }

    // 관리자 목록 검색 - 작성자(닉네임/이름)/이메일 키워드, 유형(category), 처리상태(resolved).
    // 다른 관리자 화면과 동일한 메모리 필터링 컨벤션.
    public Page<BugReportDto> getList(String keyword, String category, Boolean resolved, int page, int size) {
        List<BugReportDto> all = bugReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .filter(dto -> category == null || category.isBlank() || category.equals(dto.getCategory()))
                .filter(dto -> resolved == null || resolved == dto.isResolved())
                .filter(dto -> matches(keyword, dto.getReporterDisplay(), dto.getContactEmail(), dto.getTitle()))
                .collect(Collectors.toList());
        return PageUtils.paginate(all, page, size);
    }

    // 마이페이지 "내 문의" 탭 - 로그인 사용자가 본인이 제출한 문의만 조회(익명 제출은 계정과
    // 연결되지 않으므로 여기서 볼 수 없다 - contactEmail로만 답변을 받는다).
    public Page<BugReportDto> getMyInquiries(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<BugReportDto> mine = bugReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getReporter() != null && r.getReporter().getId().equals(user.getId()))
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageUtils.paginate(mine, page, size);
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

    public BugReportDto getDetail(Long id) {
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("버그 리포트를 찾을 수 없습니다."));
        return toDto(report);
    }

    // 답변 스레드에 새 답변 추가 - 여러 번 답변 가능(문의가 이어지는 경우). 로그인 문의자에게는
    // 인앱 알림, 비로그인(익명) 문의자에게는 이메일로만 통보한다(문의 방식 결정 - CLAUDE.md 참고).
    @Transactional
    public void addReply(Long id, String adminUsername, String content) {
        requireSuperAdmin(adminUsername);
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다."));
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("답변 내용을 입력해주세요.");
        }
        BannedWordFilter.validate(trimmed);

        InquiryReply reply = new InquiryReply();
        reply.setBugReport(report);
        reply.setAdminUsername(adminUsername);
        reply.setContent(trimmed);
        inquiryReplyRepository.save(reply);
        adminActionLogService.log("BUG_REPORT", report.getId(), "REPLY", truncate(trimmed));

        if (report.getReporter() != null) {
            notificationService.notify(report.getReporter(), Notification.Type.INQUIRY_REPLY,
                    "'" + truncate(report.getTitle()) + "' 문의에 답변이 등록되었습니다.", "/mypage/inquiries");
        } else if (report.getContactEmail() != null) {
            mailService.sendInquiryReply(report.getContactEmail(), trimmed);
        }
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

    // 첨부 DB 행을 먼저 지워야 FK 제약 위반 없이 BugReport를 지울 수 있다. 실제 파일은 여기서 직접
    // 안 지우고 EditorUploadCleanupService의 기존 24시간 미참조 정리 스케줄러가 자연스럽게 수거한다
    // (참조 테이블에서 사라졌으니 다음 정리 주기에 orphan으로 판단됨).
    @Transactional
    public void delete(Long id, String actorUsername) {
        requireSuperAdmin(actorUsername);
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("버그 리포트를 찾을 수 없습니다."));
        adminActionLogService.log("BUG_REPORT", report.getId(), "DELETE", truncate(report.getTitle()));
        bugReportAttachmentRepository.deleteAll(
                bugReportAttachmentRepository.findByBugReport_IdOrderBySortOrderAsc(report.getId()));
        inquiryReplyRepository.deleteAll(
                inquiryReplyRepository.findByBugReport_IdAndDeletedFalseOrderByCreatedAtAsc(report.getId()));
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
        List<BugReportAttachmentDto> attachments = bugReportAttachmentRepository
                .findByBugReport_IdOrderBySortOrderAsc(r.getId()).stream()
                .map(a -> BugReportAttachmentDto.builder()
                        .url(a.getUrl())
                        .originalFilename(a.getOriginalFilename())
                        .kind(a.getKind())
                        .build())
                .collect(Collectors.toList());
        List<InquiryReplyDto> replies = inquiryReplyRepository
                .findByBugReport_IdAndDeletedFalseOrderByCreatedAtAsc(r.getId()).stream()
                .map(reply -> InquiryReplyDto.builder()
                        .id(reply.getId())
                        .adminUsername(reply.getAdminUsername())
                        .content(reply.getContent())
                        .createdAt(reply.getCreatedAt().format(DISPLAY_FORMAT))
                        .build())
                .collect(Collectors.toList());

        return BugReportDto.builder()
                .id(r.getId())
                .category(r.getCategory().name())
                .categoryLabel(r.getCategory().getLabel())
                .title(r.getTitle())
                .content(r.getContent())
                .reporterDisplay(display)
                .reporterId(r.getReporter() != null ? r.getReporter().getId() : null)
                .contactEmail(r.getContactEmail())
                .resolved(r.isResolved())
                .createdAt(r.getCreatedAt().format(DISPLAY_FORMAT))
                .attachments(attachments)
                .replies(replies)
                .build();
    }
}
