package com.webschool.webschool.school.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminScheduleCommentSummaryDto {
    private Long id;
    private String schoolName;
    private String targetDate;
    private String grade;
    private String classNm;
    private String content;
    private String authorNickname; // 관리자 화면이므로 항상 실제 닉네임을 보여줌
    private int reportCount;
    private boolean blind;
    private boolean reportCleared; // 관리자가 "문제없음"으로 판결했는지 여부
    private boolean deleted;
    private String createdAt;
    private String deletedAt; // 삭제되지 않았으면 null ("삭제됨" 탭에서만 값이 있음)
}
