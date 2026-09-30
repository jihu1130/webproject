package com.webschool.webschool.global.upload;

import com.webschool.webschool.global.upload.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// 리치 에디터(게시글/오늘의 한마디 본문 중간 삽입)에서 쓰는 범용 파일 업로드. PostImageService와
// 동일한 저장 방식(FileStorageService에 위임, UUID 파일명)을 그대로 따른다.
// 예전엔 "위험한 확장자만 차단하고 나머지는 전부 허용"(블랙리스트, 2026-08-19 결정)이었는데,
// 차단 목록에 없는 .xhtml/.xml/.mht 같은 확장자가 브라우저에서 HTML로 실행돼 저장형 XSS가
// 남아 있었다(보안 점검 M1, 2026-09-30) - 이미지/동영상/문서·압축·오디오만 허용하는 허용
// 목록(화이트리스트)으로 바꿨다. 허용 목록에 없는 형식이 필요하면 FILE_EXTENSIONS에 추가하면 되고,
// 이미지/동영상이 아닌 파일은 추가해도 항상 다운로드 전용으로만 내려간다(UploadContentTypes 참고).
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileStorageService fileStorageService;

    // 이미지/동영상 목록은 UploadContentTypes의 inline 형식과 반드시 같게 유지할 것(여기서 image/video로
    // 분류돼 <img>/<video>로 삽입되는데 저장소가 다운로드 전용으로 내려주면 본문에서 깨져 보인다).
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg", "mov", "m4v");
    // "📎 파일" 버튼으로 올릴 수 있는 이미지/동영상 외 형식 - 전부 다운로드 전용으로 서빙된다.
    // svg/html/xhtml/xml처럼 브라우저가 문서로 실행할 수 있는 형식은 절대 넣지 말 것.
    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "pdf", "txt", "csv",
            "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "7z",
            "mp3", "wav", "m4a"
    );

    private static final long MAX_IMAGE_SIZE = 15L * 1024 * 1024;   // 15MB
    private static final long MAX_VIDEO_SIZE = 300L * 1024 * 1024;  // 300MB
    private static final long MAX_OTHER_SIZE = 50L * 1024 * 1024;   // 50MB
    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024; // 5MB - 프로필 사진은 에디터 첨부보다 작게 제한

    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyyMM");

    // 버그 리포트 첨부(사진/영상만 허용, 그 외 파일은 여기서 미리 걸러서 store() 자체를 안 태운다)처럼
    // "이미지/영상만" 제한이 필요한 다른 기능에서 재사용하는 공개 헬퍼.
    public boolean isImageOrVideoExtension(String filename) {
        String ext = extensionOf(filename);
        return IMAGE_EXTENSIONS.contains(ext) || VIDEO_EXTENSIONS.contains(ext);
    }

    public UploadedFileDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일을 선택해주세요.");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (ext.isEmpty()) {
            throw new IllegalArgumentException("확장자가 없는 파일은 업로드할 수 없습니다.");
        }
        if (!IMAGE_EXTENSIONS.contains(ext) && !VIDEO_EXTENSIONS.contains(ext) && !FILE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식(." + ext + ")입니다. "
                    + "사진·동영상, 문서(pdf, hwp, 오피스, txt), 압축(zip, 7z), 음성(mp3, wav, m4a)만 올릴 수 있습니다.");
        }

        String kind = IMAGE_EXTENSIONS.contains(ext) ? "image" : VIDEO_EXTENSIONS.contains(ext) ? "video" : "file";
        long maxSize = switch (kind) {
            case "image" -> MAX_IMAGE_SIZE;
            case "video" -> MAX_VIDEO_SIZE;
            default -> MAX_OTHER_SIZE;
        };
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(kind.equals("image") ? "이미지는 15MB 이하만 업로드할 수 있습니다."
                    : kind.equals("video") ? "동영상은 300MB 이하만 업로드할 수 있습니다."
                    : "파일은 50MB 이하만 업로드할 수 있습니다.");
        }

        String monthBucket = LocalDate.now().format(MONTH_BUCKET);
        String key = "editor/" + monthBucket + "/" + UUID.randomUUID() + "." + ext;

        String url;
        try {
            url = fileStorageService.store(file, key);
        } catch (IOException e) {
            log.warn("파일 저장 실패 key={}", key, e);
            throw new IllegalArgumentException("파일 저장에 실패했습니다.");
        }
        // 원본 파일명은 개인정보(실명 등)가 들어가기 쉬워 남기지 않고, 저장 키·종류·크기만.
        log.info("파일 업로드 kind={} size={}B key={}", kind, file.getSize(), key);

        return UploadedFileDto.builder()
                .url(url)
                .originalFilename(file.getOriginalFilename())
                .kind(kind)
                .build();
    }

    // 프로필 사진 업로드 - editor 업로드(store())와 달리 이미지 확장자만 허용하고, 계정당 파일이
    // 하나뿐이라 새로 올리면 기존 파일을 지운다(editor 업로드는 본문 여러 곳에서 참조될 수 있어
    // 못 지우고 EditorUploadCleanupService가 별도로 정리하지만, 프로필 사진은 User.profileImageUrl
    // 컬럼 하나만 그 파일을 가리키므로 교체 시점에 바로 지워도 안전하다).
    public String storeProfileImage(MultipartFile file, String previousUrl) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 사진을 선택해주세요.");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("이미지 파일(jpg, png, gif, webp 등)만 업로드할 수 있습니다.");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new IllegalArgumentException("프로필 사진은 5MB 이하만 업로드할 수 있습니다.");
        }

        String key = "profile/" + UUID.randomUUID() + "." + ext;

        String url;
        try {
            url = fileStorageService.store(file, key);
        } catch (IOException e) {
            log.warn("프로필 사진 저장 실패 key={}", key, e);
            throw new IllegalArgumentException("사진 저장에 실패했습니다.");
        }
        log.info("프로필 사진 업로드 size={}B key={}", file.getSize(), key);

        deleteProfileImage(previousUrl);
        return url;
    }

    // 기본 이미지로 되돌리거나 새 사진으로 교체할 때 이전 파일을 지운다. previousUrl은 항상
    // storeProfileImage()가 예전에 반환했던 값이거나 null(업로드한 적 없음)뿐이라 별도 검증 없이
    // 그대로 FileStorageService에 위임해도 안전하다. 실패해도(이미 없거나 권한 문제 등) 업로드/
    // 되돌리기 자체를 막을 이유는 없어서 예외를 던지지 않고 조용히 넘어간다(delete() 자체가 best-effort).
    public void deleteProfileImage(String url) {
        if (url == null) {
            return;
        }
        fileStorageService.delete(url);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
