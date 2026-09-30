package com.webschool.webschool.global.upload;

import com.webschool.webschool.global.upload.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 보안 점검 M1(2026-09-30) 회귀 테스트 - 에디터 업로드가 허용 목록 방식으로 바뀐 것을 고정한다.
class FileUploadServiceTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final FileUploadService service = new FileUploadService(storage);

    // 예전 블랙리스트를 빠져나가던 확장자들 - 브라우저가 HTML 문서로 실행할 수 있다.
    @ParameterizedTest
    @ValueSource(strings = {"xss.xhtml", "xss.xht", "xss.xml", "xss.shtml", "xss.mht", "xss.mhtml",
            "xss.svgz", "xss.html", "xss.HTM", "xss.svg", "run.exe", "noext."})
    void browserExecutableOrUnknownExtensionsAreRejected(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/html", "<script>alert(1)</script>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.store(file));
        verify(storage, never()).store(any(org.springframework.web.multipart.MultipartFile.class), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.PNG", "clip.mp4", "report.pdf", "과제.hwp", "notes.txt", "data.zip"})
    void allowedExtensionsAreStored(String filename) throws Exception {
        when(storage.store(any(org.springframework.web.multipart.MultipartFile.class), anyString())).thenReturn("/uploads/x");
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", new byte[]{1, 2, 3});

        assertEquals("/uploads/x", service.store(file).getUrl());
    }

    @Test
    void kindIsDecidedByExtension() throws Exception {
        when(storage.store(any(org.springframework.web.multipart.MultipartFile.class), anyString())).thenReturn("/uploads/x");

        assertEquals("image", service.store(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1})).getKind());
        assertEquals("video", service.store(new MockMultipartFile("file", "a.webm", "video/webm", new byte[]{1})).getKind());
        assertEquals("file", service.store(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})).getKind());
    }
}
