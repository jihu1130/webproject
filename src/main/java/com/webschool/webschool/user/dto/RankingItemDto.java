package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 포인트/티어 랭킹 페이지(todo.md 요구사항) - PublicUserProfileDto와 동일하게 학교/학년/반 등
// 개인정보는 담지 않는다(공개 프로필과 같은 노출 수준). rank는 DB 컬럼이 아니라 서비스 단에서
// 페이지 offset + 목록 내 순번으로 계산한 값(동점자는 그냥 조회 순서대로 순번이 매겨짐 - 티어
// 시스템 자체가 "정확한 석차"보다 "대략적인 위치"를 보여주는 목적이라 동점 처리 규칙까지는 필요 없음).
@Getter
@Builder
public class RankingItemDto {
    private int rank;
    private String uuid; // 공개 프로필 링크(/users/{uuid})용
    private String nickname;
    private String profileImageUrl;
    private int points;
    private String tierLabel;
    private String equippedTitle;
    private String equippedAvatarColor;
    private String equippedEffect;
}
