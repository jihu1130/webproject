package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 마이페이지 "내가 쓴 글 관리" - 게시글 탭. 본인 글이라 익명(ANONYMOUS) 카테고리도 그대로 포함한다.
@Getter
@Builder
public class MyPostSummaryDto {
    private String uuid;
    private String title;
    private String categoryLabel;
    private String createdAt;
    private int viewCount;
    private boolean blind;
}
