package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PostContestEntryDto {
    private Long entryId;
    private String postUuid;
    private String postTitle;
    private String authorNickname;
    private int voteCount;
    private boolean votedByMe;
    private boolean mine; // 내가 신청한 후보인지 - 투표 버튼을 숨기는 데 씀(본인 후보 투표 금지)
}
