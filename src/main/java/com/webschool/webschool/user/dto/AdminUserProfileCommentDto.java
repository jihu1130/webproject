package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// AdminUserProfileDto 안에 들어가는 "최근 작성 댓글" 요약 (AdminUserProfilePostDto와 동일 모양) -
// 댓글 자체는 상세 페이지가 없어서 부모 게시글 상세의 앵커(#comment-{id})로 바로 이동시킨다
// (admin/comment-list.html이 이미 쓰는 것과 동일한 패턴).
@Getter
@Builder
public class AdminUserProfileCommentDto {
    private Long id;
    private String content;
    private Long postId;
    private String postTitle;
    private String createdAt;
}
