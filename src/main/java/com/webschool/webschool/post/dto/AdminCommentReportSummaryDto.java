package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

// 관리자 "신고 관리 > 댓글" 탭 목록용 DTO. 댓글 자체의 상세 화면은 없으므로
// postId/postTitle을 함께 내려서 부모 게시글의 관리자 상세 화면으로 이동시킨다.
@Getter
@Builder
public class AdminCommentReportSummaryDto {
    private Long id;
    private Long postId;
    private String postTitle;
    private String content;
    private String authorNickname;
    private Long authorId; // 프로필(/admin/profiles/{id}) 링크용
    private int reportCount;
    private boolean blind;
    private boolean reportCleared;
    private String createdAt;
    private boolean deleted; // 댓글 관리 "삭제됨" 탭에서만 의미 있음(다른 곳에선 항상 false)
    private String deletedAt; // 삭제되지 않았으면 null
}
