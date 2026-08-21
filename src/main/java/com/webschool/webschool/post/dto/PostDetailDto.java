package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostDetailDto {
    private Long id; // 내부용 (관리자 링크 등) - 공개 URL에는 uuid를 쓴다
    private String uuid;
    private String title;
    private String content;
    private String nickname; // 익명 카테고리인 경우 "익명"으로 대체된 값
    private Long authorId; // 프로필(/users/{id}) 링크용
    private boolean authorLinkable; // 익명 게시물이거나 작성자가 탈퇴한 경우 false - 이때는 링크를 걸지 않는다
    private String category;
    private String categoryLabel;
    private String createdAt;
    private int viewCount;
    private int reportCount;
    private int likeCount;
    private boolean likedByMe; // 현재 로그인한 사용자가 이미 좋아요를 눌렀는지 여부
    private boolean bookmarkedByMe; // 현재 로그인한 사용자가 이미 북마크했는지 여부
    private boolean blind;
    private boolean edited; // 수정된 게시물인지 여부
    private boolean mine; // 현재 로그인한 사용자가 작성한 게시물인지 여부
    private boolean reportedByMe; // 현재 로그인한 사용자가 이미 이 게시물을 신고했는지 여부
    private boolean unlisted; // 목록/검색엔 안 뜨고 링크로만 공개된 게시물인지 여부
}
