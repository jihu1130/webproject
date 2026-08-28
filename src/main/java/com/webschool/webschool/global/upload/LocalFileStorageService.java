package com.webschool.webschool.global.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 기본 저장소 - app.upload.s3.bucket이 설정되지 않은 환경(로컬 개발, S3 전환 전 서버)에서
// 활성화된다(FileStorageConfig 참고). PostImageService/FileUploadService가 예전에 직접
// 하던 Files.copy/deleteIfExists 로직을 그대로 옮겨온 것뿐이라 동작은 기존과 완전히 동일하다.
public class LocalFileStorageService implements FileStorageService {

    private final String uploadDir;

    public LocalFileStorageService(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String store(MultipartFile file, String key) throws IOException {
        Path target = baseDir().resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + key;
    }

    @Override
    public void delete(String url) {
        if (url == null) {
            return;
        }
        String key = url.startsWith("/uploads/") ? url.substring("/uploads/".length()) : url;
        try {
            Files.deleteIfExists(baseDir().resolve(key));
        } catch (IOException ignored) {
            // best-effort 정리 - 실패해도 무시(기존 PostImageService/FileUploadService와 동일한 관례)
        }
    }

    @Override
    public List<StoredEntry> list(String prefix) throws IOException {
        Path dir = baseDir().resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(this::toEntry)
                    .collect(Collectors.toList());
        }
    }

    private StoredEntry toEntry(Path file) {
        try {
            String relative = baseDir().relativize(file).toString().replace('\\', '/');
            return new StoredEntry("/uploads/" + relative, Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private Path baseDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}
