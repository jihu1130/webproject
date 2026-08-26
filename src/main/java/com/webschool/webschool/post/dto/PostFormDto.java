package com.webschool.webschool.post.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PostFormDto {
    private String title;
    private String content;
    private String category; // Post.Category enum name (FREE / ANONYMOUS / QNA)
    // 공개범위 - Post.Visibility enum name (PUBLIC / UNLISTED / PRIVATE). 카테고리와 동일하게
    // 문자열로 받고 PostService.parseVisibility()가 검증한다(잘못된 값은 PUBLIC으로 폴백).
    private String visibility;
}
