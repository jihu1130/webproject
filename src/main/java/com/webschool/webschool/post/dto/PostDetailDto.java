package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostDetailDto {
    private Long id; // 내부용 (관리자 링크 등) - 공개 URL에는 uuid를 쓴다
    private String uuid;
    private String title;
    private String content;
    private String nickname; // 익명 카테고리인 경우 "익명"으로 대체된 값
    private String category;
    private String categoryLabel;
    private String createdAt;
    private int viewCount;
    private int reportCount;
    private boolean blind;
    private boolean edited; // 수정된 게시물인지 여부
    private boolean mine; // 현재 로그인한 사용자가 작성한 게시물인지 여부
}
