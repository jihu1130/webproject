package com.webschool.webschool.global.upload.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 보안 점검 M1(2026-09-30) - 운영(S3)에서 클라이언트가 보낸 Content-Type을 그대로 저장하던 문제의 회귀 테스트.
class S3FileStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3FileStorageService service =
            new S3FileStorageService(s3Client, "bucket", "ap-northeast-2", "");

    private PutObjectRequest storeAndCapture(MockMultipartFile file, String key) throws Exception {
        service.store(file, key);
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }

    @Test
    void clientContentTypeIsIgnoredForImages() throws Exception {
        // 확장자는 .png인데 Content-Type을 text/html로 보낸 요청 - 예전엔 S3가 HTML로 서빙했다.
        PutObjectRequest request = storeAndCapture(
                new MockMultipartFile("file", "a.png", "text/html", "<script>".getBytes()), "editor/202609/x.png");

        assertEquals("image/png", request.contentType());
        assertNull(request.contentDisposition());
    }

    @Test
    void nonMediaFilesAreDownloadOnlyWithOriginalName() throws Exception {
        PutObjectRequest request = storeAndCapture(
                new MockMultipartFile("file", "과제 보고서.pdf", "text/html", new byte[]{1}), "editor/202609/x.pdf");

        assertEquals(UploadContentTypes.DOWNLOAD_ONLY_TYPE, request.contentType());
        assertTrue(request.contentDisposition().startsWith("attachment"), request.contentDisposition());
        assertTrue(request.contentDisposition().contains("filename*=UTF-8''"), request.contentDisposition());
    }
}
