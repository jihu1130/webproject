package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostImageDto {
    private Long id;
    private String url;
    private String originalFilename;
    private int sortOrder;
}
