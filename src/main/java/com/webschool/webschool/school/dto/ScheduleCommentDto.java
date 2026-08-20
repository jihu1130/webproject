package com.webschool.webschool.school.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScheduleCommentDto {
    private Long id;
    private String nickname;
    private Long authorId; // 프로필(/users/{id}) 링크용
    private boolean authorLinkable; // 작성자가 탈퇴한 경우 false
    private String content;
    private String createdAt;
    private boolean edited; // 수정된 댓글인지 여부
    private boolean mine; // 현재 로그인한 사용자가 작성한 댓글인지 여부
    private boolean blind; // 신고 누적으로 블라인드 처리된 한마디인지 여부 (작성자 본인/관리자가 아니면 content는 이미 안내문구로 대체됨)
    private boolean reportedByMe; // 현재 로그인한 사용자가 이미 이 한마디를 신고했는지 여부
    private int likeCount;
    private boolean likedByMe;
    private boolean bookmarkedByMe;
}
