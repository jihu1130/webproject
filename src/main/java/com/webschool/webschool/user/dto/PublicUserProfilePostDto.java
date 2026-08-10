package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicUserProfilePostDto {
    private String uuid; // 공개 URL(/posts/{uuid}) 링크용
    private String title;
    private String categoryLabel;
    private String createdAt;
    private int viewCount;
}
