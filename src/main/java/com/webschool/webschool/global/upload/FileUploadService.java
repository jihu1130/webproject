package com.webschool.webschool.global.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// 리치 에디터(게시글/오늘의 한마디 본문 중간 삽입)에서 쓰는 범용 파일 업로드. PostImageService와
// 동일한 저장 방식(프로젝트 밖 app.upload.dir에 실제 파일, UUID 파일명)을 그대로 따르되, 이미지
// 확장자만 허용하던 그쪽과 달리 여기는 "위험한 실행/스크립트 파일만 차단하고 나머지는 전부 허용"
// 방식이다(2026-08-19 사용자 확정) - 업로드된 파일은 항상 /uploads/**로 정적 서빙만 되고 서버에서
// 실행되지 않으므로, 그래도 남아있는 위험은 "다른 사용자가 다운로드해서 자기 PC에서 직접 실행"하는
// 경우뿐이라 확장자 차단 정도로 충분하다.
@Service
public class FileUploadService {

    // 서버/클라이언트에서 직접 실행되거나 설치될 수 있는 확장자만 차단(화이트리스트가 아니라 블랙리스트).
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "msp", "scr", "pif", "gadget",
            "sh", "bash", "run", "app", "pkg", "deb", "rpm", "apk", "ipa",
            "jar", "js", "jse", "vbs", "vbe", "wsf", "wsh", "ps1", "psm1",
            "jsp", "jspx", "php", "php3", "php4", "php5", "phtml", "asp", "aspx",
            "cgi", "dll", "so", "action", "reg", "hta"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "avif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "ogg", "mov", "m4v");

    private static final long MAX_IMAGE_SIZE = 15L * 1024 * 1024;   // 15MB
    private static final long MAX_VIDEO_SIZE = 300L * 1024 * 1024;  // 300MB
    private static final long MAX_OTHER_SIZE = 50L * 1024 * 1024;   // 50MB
    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024; // 5MB - 프로필 사진은 에디터 첨부보다 작게 제한

    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyyMM");

    @Value("${app.upload.dir}")
    private String uploadDir;

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
        if (DANGEROUS_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("실행/스크립트 파일(." + ext + ")은 업로드할 수 없습니다.");
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
        String storedFilename = UUID.randomUUID() + "." + ext;
        Path dir = baseDir().resolve("editor").resolve(monthBucket);

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException("파일 저장에 실패했습니다.");
        }

        return UploadedFileDto.builder()
                .url("/uploads/editor/" + monthBucket + "/" + storedFilename)
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

        String storedFilename = UUID.randomUUID() + "." + ext;
        Path dir = baseDir().resolve("profile");

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException("사진 저장에 실패했습니다.");
        }

        deleteProfileImage(previousUrl);
        return "/uploads/profile/" + storedFilename;
    }

    // 기본 이미지로 되돌리거나 새 사진으로 교체할 때 이전 파일을 지운다. 실패해도(이미 없거나
    // 권한 문제 등) 업로드/되돌리기 자체를 막을 이유는 없어서 예외를 던지지 않고 조용히 넘어간다.
    public void deleteProfileImage(String url) {
        if (url == null || !url.startsWith("/uploads/profile/")) {
            return;
        }
        try {
            Files.deleteIfExists(baseDir().resolve(url.substring("/uploads/".length())));
        } catch (IOException ignored) {
            // best-effort 정리 - 실패해도 무시
        }
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

    private Path baseDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}
