package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 마이페이지 프로필 카드 통계 바(게시글/댓글/받은 좋아요) - MyActivityService.getStats()가 채운다.
// 프로필_디자인.md 설계 반영.
@Getter
@Builder
public class MyPageStatsDto {
    private long postCount;
    private long commentCount;
    private long likeCount;
}
