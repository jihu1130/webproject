package com.webschool.webschool.poll.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class PollVoteRequest {
    private List<Long> optionIds;
    private String customOptionText; // allowCustomOption=true일 때만 의미 있음
}
