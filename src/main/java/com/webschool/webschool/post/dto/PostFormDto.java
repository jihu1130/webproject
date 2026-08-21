package com.webschool.webschool.post.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PostFormDto {
    private String title;
    private String content;
    private String category; // Post.Category enum name (FREE / ANONYMOUS / QNA)
    private boolean unlisted; // true면 Post.Visibility.UNLISTED(목록/검색엔 안 뜨지만 링크로는 열람 가능)
}
