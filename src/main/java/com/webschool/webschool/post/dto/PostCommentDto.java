package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostCommentDto {
    private Long id;
    private String nickname;
    private String content;
    private String createdAt;
    private boolean edited;
    private boolean mine;
    private boolean blind; // 신고 누적으로 블라인드 처리된 댓글인지 여부 (작성자 본인/관리자가 아니면 content는 이미 안내문구로 대체됨)
}
