package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 마이페이지 "내 활동내역" - 댓글 탭. 소속 게시글로 바로 이동할 수 있게 postUuid/postTitle도 담는다.
@Getter
@Builder
public class MyCommentSummaryDto {
    private Long id;
    private String content;
    private String createdAt;
    private String postUuid;
    private String postTitle;
    private boolean blind;
}
