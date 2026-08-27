package com.webschool.webschool.post.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter @Builder
public class ContestWeekResultDto {
    private LocalDate weekStart;
    private List<PostContestResultDto> results;
}
