package com.webschool.webschool.global.upload.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 보안 점검 M1(2026-09-30) - Content-Type은 클라이언트 값이 아니라 확장자로만 결정되고,
// 이미지/동영상이 아니면 다운로드 전용이어야 한다.
class UploadContentTypesTest {

    @Test
    void imagesAndVideosAreInlineWithFixedType() {
        assertTrue(UploadContentTypes.isInline("editor/202609/a.PNG"));
        assertEquals("image/png", UploadContentTypes.contentTypeFor("editor/202609/a.PNG"));
        assertEquals("video/mp4", UploadContentTypes.contentTypeFor("/uploads/editor/202609/a.mp4"));
    }

    @Test
    void everythingElseIsDownloadOnly() {
        for (String key : new String[]{"a.html", "a.xhtml", "a.xml", "a.svg", "a.pdf", "a.txt", "noext", "dir.png/file"}) {
            assertFalse(UploadContentTypes.isInline(key), key);
            assertEquals(UploadContentTypes.DOWNLOAD_ONLY_TYPE, UploadContentTypes.contentTypeFor(key), key);
        }
    }
}
