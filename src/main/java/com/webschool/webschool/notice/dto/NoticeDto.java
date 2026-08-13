package com.webschool.webschool.notice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NoticeDto {
    private Long id;
    private String title;
    private String content;
    private String authorNickname;
    private boolean active;
    private String createdAt;
}
