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

    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyyMM");

    @Value("${app.upload.dir}")
    private String uploadDir;

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
