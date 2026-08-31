package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostCommentDto {
    private Long id;
    private Long parentId; // null이면 최상위 댓글, 아니면 답글이 달린 부모 댓글 id(1depth만 허용)
    private String nickname;
    private Long authorId; // 차단(/users/blocks) 액션 파라미터용 - URL에는 노출 안 함
    private String authorUuid; // 프로필(/users/{uuid}) 링크용
    private boolean authorLinkable; // 작성자가 탈퇴한 경우 false
    private String content;
    private String createdAt;
    private boolean edited;
    private boolean mine;
    private boolean blind; // 신고 누적으로 블라인드 처리된 댓글인지 여부 (작성자 본인/관리자가 아니면 content는 이미 안내문구로 대체됨)
    private boolean reportedByMe; // 현재 로그인한 사용자가 이미 이 댓글을 신고했는지 여부
    private int likeCount;
    private boolean likedByMe;
    private boolean bookmarkedByMe;
    private boolean accepted; // QNA 게시글에서 질문자가 채택한 답변인지 여부
}
