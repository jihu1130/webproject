package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter @Builder
public class PostDailyBestResultDto {
    private LocalDate resultDate;
    private String postUuid;
    private String postTitle;
    private String authorNickname;
    private int recommendCount;
    private int prizePoints;
}
