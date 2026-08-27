package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PostContestResultDto {
    private int rank;
    private String postUuid;
    private String postTitle;
    private String authorNickname;
    private int voteCount;
    private int prizePoints;
}
