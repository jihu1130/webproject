package com.webschool.webschool.global.upload;

import com.webschool.webschool.bugreport.repository.BugReportAttachmentRepository;
import com.webschool.webschool.post.repository.PostRepository;
import com.webschool.webschool.school.repository.ScheduleCommentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 수정사항.md 지적(#5) - 리치 에디터로 /api/uploads/editor에 올린 파일은 DB에 전혀 기록되지 않고
// 저장소(로컬/S3)에만 존재한다(FileUploadService 참고). 글쓰기 중 삽입해놓고 저장을 안 누르면 그
// 파일만 editor/에 고아로 계속 쌓이는 문제 - 이 프로젝트의 첫 @Scheduled 작업으로 매일 한 번 정리한다.
//
// 방식: Post/ScheduleComment의 content(소프트 삭제 여부 무관 - 삭제된 글도 본문은 DB에 남아있고
// 그 안 파일 참조는 여전히 "쓰이는 중"이라 봐야 함, 하드 삭제 전까지는)에서 실제로 참조되는 URL
// 집합을 정규식으로 뽑아낸 뒤, FileStorageService.list()가 나열한 파일 중 그 집합에 없으면서
// 수정시각이 24시간 이상 지난 것만 지운다(방금 올렸지만 아직 저장 버튼을 안 누른 파일을 실수로
// 지우지 않기 위한 유예). S3 전환(2026-08-28) 이후에도 이 배치가 계속 동작하도록 로컬 디스크
// 직접 순회 대신 FileStorageService.list()로 백엔드에 상관없이 나열한다 - 이걸 안 하면 S3에서
// 저장 안 누른 임시 업로드가 영구히 쌓여 비용이 계속 늘어난다.
@Service
@RequiredArgsConstructor
public class EditorUploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(EditorUploadCleanupService.class);
    // 로컬("/uploads/editor/...")과 S3(전체 https URL) 양쪽 형식을 다 인식해야 한다.
    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("(/uploads/editor/[\\w\\-./]+|https?://[\\w.\\-]+/editor/[\\w\\-./]+)");
    private static final long GRACE_PERIOD_HOURS = 24;

    private final PostRepository postRepository;
    private final ScheduleCommentRepository scheduleCommentRepository;
    // 버그 리포트 첨부(BugReportService)도 이 폴더를 재사용한다 - 본문 텍스트 안에 URL이 박히는
    // Post/ScheduleComment와 달리 별도 첨부 테이블(url 컬럼)로 참조하므로 정규식 스캔 대신 직접 조회.
    private final BugReportAttachmentRepository bugReportAttachmentRepository;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOrphanedUploads() {
        Set<String> referenced = collectReferencedPaths();

        List<FileStorageService.StoredEntry> entries;
        try {
            entries = fileStorageService.list("editor/");
        } catch (IOException e) {
            log.warn("임시 업로드 정리 중 목록 조회 실패", e);
            return;
        }

        Instant cutoff = Instant.now().minus(GRACE_PERIOD_HOURS, ChronoUnit.HOURS);
        int deleted = 0;

        for (FileStorageService.StoredEntry entry : entries) {
            if (referenced.contains(entry.url())) {
                continue;
            }
            if (entry.lastModified().isAfter(cutoff)) {
                continue; // 아직 유예 기간 안 - 방금 올렸지만 저장 전일 수 있음
            }
            fileStorageService.delete(entry.url());
            deleted++;
        }

        if (deleted > 0 || !entries.isEmpty()) {
            log.info("임시 업로드 정리 완료 - 스캔 {}개, 삭제 {}개, 참조 중 {}개", entries.size(), deleted, referenced.size());
        }
    }

    private Set<String> collectReferencedPaths() {
        Set<String> referenced = new HashSet<>();
        postRepository.findAll().forEach(p -> addReferences(referenced, p.getContent()));
        scheduleCommentRepository.findAll().forEach(c -> addReferences(referenced, c.getContent()));
        bugReportAttachmentRepository.findAll().forEach(a -> referenced.add(a.getUrl()));
        return referenced;
    }

    private void addReferences(Set<String> referenced, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        Matcher matcher = REFERENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            referenced.add(matcher.group());
        }
    }
}
