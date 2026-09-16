package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PostRecommendRankDto {
    private int rank;
    private String postUuid;
    private String postTitle;
    private String authorNickname;
    private long recommendCount;
    private boolean recommendedByMe;
    private boolean mine; // 내 글인지 - 추천 버튼을 숨기는 데 씀(본인 글 추천 금지)
}
