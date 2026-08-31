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
    private Long authorId; // 내부용 - URL에는 노출 안 함
    private String authorUuid; // 프로필(/users/{uuid}) 링크용
    private boolean authorLinkable; // 익명 게시물이거나 작성자가 탈퇴한 경우 false - 이때는 링크를 걸지 않는다
    private String category;
    private String categoryLabel;
    private String thumbnailUrl; // 대표 이미지(첫 번째 첨부 이미지) - 없으면 null
    private String createdAt;
    private int viewCount;
    private int likeCount;
}
