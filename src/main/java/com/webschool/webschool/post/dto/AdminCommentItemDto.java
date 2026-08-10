package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCommentItemDto {
    private Long id;
    private String authorNickname;
    private String content;
    private String createdAt;
    private boolean deleted; // 소프트 삭제된 댓글인지 여부 - 관리자 화면에서는 삭제된 댓글도 함께 보여줌
    private int reportCount;
    private boolean blind; // 관리자에게는 블라인드 여부와 무관하게 항상 원본 content를 보여줌
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부
}
