package com.webschool.webschool.bugreport.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryReplyDto {
    private Long id;
    private String adminUsername;
    private String content;
    private String createdAt;
}
