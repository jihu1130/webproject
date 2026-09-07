package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 티어 안내 페이지(사용자 요청) - PointTier 각 등급의 포인트 구간. maxPoints는 최고 등급(MASTER)에서
// null(템플릿이 "이상"으로 표시) - PointTier 자체엔 상한 개념이 없어서(다음 등급 min에서 역산) 별도
// DTO로 계산해서 넘긴다.
@Getter
@Builder
public class TierRangeDto {
    private String label;
    private int minPoints;
    private Integer maxPoints;
}
