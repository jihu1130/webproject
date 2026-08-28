package com.webschool.webschool.poll.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class PollAdminOptionDto {
    private String label;
    private int voteCount;
    private long percent; // 이 설문 전체 투표수 대비 비율(반올림) - static/js/poll.js의 pct 계산과 동일한 정의
    // 익명 설문이면 PollService가 이미 빈 리스트로 리댁션한 값을 그대로 물려받는다(관리자도 예외 없음).
    private List<String> voterNicknames;
}
