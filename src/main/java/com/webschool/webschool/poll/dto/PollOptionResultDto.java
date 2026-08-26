package com.webschool.webschool.poll.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class PollOptionResultDto {
    private Long id;
    private String label;
    private int voteCount;
    private boolean votedByMe;
    // 익명 설문이면 항상 빈 리스트(PollService가 리댁션) - 익명 아닌 설문에서만 누가 골랐는지 공개
    private List<String> voterNicknames;
}
