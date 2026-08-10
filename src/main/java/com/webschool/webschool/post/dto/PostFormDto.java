package com.webschool.webschool.post.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PostFormDto {
    private String title;
    private String content;
    private String category; // Post.Category enum name (FREE / ANONYMOUS / QNA)
}
