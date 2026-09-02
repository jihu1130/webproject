package com.webschool.webschool.global.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

// 파일 저장 방식(로컬 디스크/S3)을 감추는 추상화. PostImageService/FileUploadService는
// 이 인터페이스만 알면 되고, 실제 저장소는 app.upload.s3.bucket 설정 유무로 갈린다
// (LocalFileStorageService/S3FileStorageService 참고).
public interface FileStorageService {

    // key로 저장한 뒤 바로 <img src>/DB에 쓸 수 있는 URL을 반환한다.
    String store(MultipartFile file, String key) throws IOException;

    // 업로드된 원본이 아니라 서버가 그 자리에서 만들어낸 바이트(예: PostImageService가 리사이즈한
    // 목록용 썸네일)를 저장할 때 쓴다 - MultipartFile로 감쌀 필요 없이 바로 저장.
    String store(byte[] data, String contentType, String key) throws IOException;

    // store()가 반환했던 URL을 그대로 넘기면 알아서 해당 파일을 지운다(best-effort).
    void delete(String url);

    // prefix(예: "editor/") 아래 저장된 모든 오브젝트를 나열한다. store()/delete()와 달리
    // EditorUploadCleanupService(고아 파일 정리 배치)만 쓰는 부가 기능 - 로컬은 디스크 순회,
    // S3는 ListObjectsV2로 구현한다.
    List<StoredEntry> list(String prefix) throws IOException;

    record StoredEntry(String url, Instant lastModified) {
    }
}
