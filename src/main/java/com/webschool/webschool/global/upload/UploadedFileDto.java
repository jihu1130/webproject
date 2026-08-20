package com.webschool.webschool.global.upload;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadedFileDto {
    private String url;
    private String originalFilename;
    private String kind; // image | video | file - 에디터가 삽입할 태그(img/video/a)를 고르는 기준
}
