package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminPostDetailDto {
    private Long id;
    private String uuid; // 공개 URL(/posts/{uuid}) 링크용
    private String title;
    private String content;
    private String authorNickname; // 관리자 화면이므로 익명 게시물이어도 실제 닉네임을 보여줌
    private Long authorId; // 프로필(/admin/profiles/{id}) 링크용
    private String authorUsername;
    private String category;
    private String categoryLabel;
    private int viewCount;
    private int reportCount;
    private boolean blind;
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부
    private boolean deleted;
    private String deletedAt; // 삭제되지 않았으면 null
    private String createdAt;
    private boolean edited;
    private List<AdminReportItemDto> reports;
    private List<PostImageDto> images;
    private List<AdminCommentItemDto> comments; // 삭제된 댓글도 포함 (deleted 플래그로 구분)
}
