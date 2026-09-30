package com.webschool.webschool.post.recommend.repository;

// PostRecommendRepository의 기간별 랭킹 집계 쿼리(GROUP BY post) 결과 프로젝션.
public interface PostRecommendCount {
    Long getPostId();

    long getCount();
}
