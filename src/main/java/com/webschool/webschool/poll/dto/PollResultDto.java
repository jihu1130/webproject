package com.webschool.webschool.poll.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class PollResultDto {
    private Long id;
    private String question;
    private boolean allowMultiple;
    private boolean allowCustomOption;
    private boolean anonymous;
    private String visibilityScope;
    private String expiresAt; // 위젯 표시용 포맷 문자열 - null이면 마감 없음
    private boolean expired;
    private int totalVoters;
    private boolean votedByMe;
    private boolean mine; // 현재 사용자가 이 설문의 작성자인지
    private List<PollOptionResultDto> options;
}
