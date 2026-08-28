package com.webschool.webschool.poll.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PollAdminSummaryDto {
    private Long id;
    private String question;
    private String creatorNickname;
    private String targetType; // "게시글" / "한마디"
    private String targetLabel; // 게시글 제목 또는 한마디 날짜
    private String visibilityScopeLabel;
    private boolean anonymous;
    private boolean allowMultiple;
    private int optionCount;
    private long totalVoters;
    private String createdAt;
}
