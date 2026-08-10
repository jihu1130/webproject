package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostListItemDto {
    private Long id; // 내부용 (관리자 링크 등) - 공개 URL에는 uuid를 쓴다
    private String uuid;
    private String title;
    private String nickname; // 익명 카테고리인 경우 "익명"으로 대체된 값
    private String category;
    private String categoryLabel;
    private String createdAt;
    private int viewCount;
}
