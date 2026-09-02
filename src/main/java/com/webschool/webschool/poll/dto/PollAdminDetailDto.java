package com.webschool.webschool.poll.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class PollAdminDetailDto {
    private Long id;
    private String question;
    private String creatorNickname;
    private String targetType;
    private String targetLabel;
    private String visibilityScopeLabel;
    private boolean anonymous;
    private boolean allowMultiple;
    private boolean allowCustomOption;
    private String createdAt;
    private long totalVoters;
    private int totalVotes;
    private List<PollAdminOptionDto> options;
    private boolean deleted;
    private String deletedAt; // 삭제되지 않았으면 null
}
