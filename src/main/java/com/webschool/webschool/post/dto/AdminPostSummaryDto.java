package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPostSummaryDto {
    private Long id;
    private String title;
    private String category;
    private String categoryLabel;
    private String authorNickname; // 관리자 화면이므로 익명 게시물이어도 실제 닉네임을 보여줌
    private int reportCount;
    private boolean blind;
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부
    private boolean deleted;
    private String createdAt;
    private String latestReportAt; // 신고가 없으면 null (관리자가 수동 블라인드한 경우)
    private String deletedAt; // 삭제되지 않았으면 null ("삭제됨" 탭에서만 값이 있음)
}
