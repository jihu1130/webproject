package com.webschool.webschool.user.dto;

import lombok.Builder;
import lombok.Getter;

// 마이페이지 "내가 쓴 글 관리" - 오늘의 한마디 탭.
@Getter
@Builder
public class MyScheduleCommentSummaryDto {
    private Long id;
    private String uuid; // 공개 URL(/school/comments/{uuid})용 - id는 삭제 폼 액션 등 내부 액션에만 쓰인다
    private String content;
    private String targetDate;
    private String createdAt;
    private String schoolName;
    private String grade;
    private String classNm;
    private boolean blind;
}
