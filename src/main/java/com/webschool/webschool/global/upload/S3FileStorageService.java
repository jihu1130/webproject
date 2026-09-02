package com.webschool.webschool.global.upload;

import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// app.upload.s3.bucket이 설정된 환경(운영 서버)에서 활성화되는 저장소(FileStorageConfig 참고).
// EC2 인스턴스 역할(IAM)로 인증되므로 이 클래스/application.yml 어디에도 액세스 키를 넣지 않는다
// (AWS.md "자격증명은 개인 컴퓨터에만" 원칙 - S3Client 빈은 SDK 기본 자격증명 체인을 그대로 씀).
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String baseUrl; // 비어있으면 표준 S3 버추얼호스팅 URL을 그대로 씀

    public S3FileStorageService(S3Client s3Client, String bucket, String region, String baseUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://" + bucket + ".s3." + region + ".amazonaws.com"
                : baseUrl.replaceAll("/+$", "");
    }

    @Override
    public String store(MultipartFile file, String key) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return baseUrl + "/" + key;
    }

    @Override
    public String store(byte[] data, String contentType, String key) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(data));
        return baseUrl + "/" + key;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(baseUrl + "/")) {
            return; // 이 저장소가 만든 URL이 아니면(예: 마이그레이션 전 로컬 경로) 손대지 않는다
        }
        String key = url.substring((baseUrl + "/").length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ignored) {
            // best-effort 정리 - 실패해도 무시(LocalFileStorageService와 동일한 관례)
        }
    }

    @Override
    public List<StoredEntry> list(String prefix) {
        List<StoredEntry> entries = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        ListObjectsV2Iterable pages = s3Client.listObjectsV2Paginator(request);
        for (S3Object object : pages.contents()) {
            entries.add(new StoredEntry(baseUrl + "/" + object.key(), object.lastModified()));
        }
        return entries;
    }
}
