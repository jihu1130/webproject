package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

// 커뮤니티(일반 사용자)에서 작성자 이름을 눌렀을 때 보여주는 최소 공개 프로필 - 관리자 전용
// AdminUserProfileDto와 달리 학교/학년/반 등 개인정보는 포함하지 않는다(사용자 요청에 따른 설계
// 결정: 닉네임 + 작성 글 목록만). 익명 카테고리 글은 posts에 아예 안 담긴다(PostRepository 참고).
@Getter
@Builder
public class PublicUserProfileDto {
    private Long id; // 내부용(관리자 화면 등) - 공개 URL에는 uuid를 쓴다
    private String uuid; // 공개 프로필 URL(/users/{uuid}) 자기 링크(페이지네이션)용
    private String nickname;
    private String bio;
    private String profileImageUrl;
    private long postCount; // 프로필 카드 통계 바(프로필_디자인.md) - posts의 totalElements와 동일한 값
    private long commentCount; // 마이페이지와 동일한 집계(post.PostCommentRepository), 어느 글에 달았는지는 노출 안 함
    private long likeCount; // 마이페이지와 동일한 집계(PostRepository.sumLikeCountByAuthor_Id), 어느 글이 받았는지는 노출 안 함
    private int points; // 포인트/티어 시스템(todo.md 요구사항)
    private String tierLabel;
    private String equippedTitle;       // 포인트 소비 상점(todo.md 요구사항)
    private String equippedAvatarColor;
    private String equippedEffect;
    private Page<PublicUserProfilePostDto> posts;
}
