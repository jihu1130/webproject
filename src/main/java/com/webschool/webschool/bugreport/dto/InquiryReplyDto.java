package com.webschool.webschool.bugreport.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryReplyDto {
    private Long id;
    private boolean fromAdmin;
    private String authorDisplay; // 관리자면 "관리자", 제출자 본인이면 닉네임
    private String content;
    private String createdAt;
}
