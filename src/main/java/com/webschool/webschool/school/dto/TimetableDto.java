package com.webschool.webschool.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableDto {
    private String perio;   // 교시 (1교시, 2교시...)
    private String subject; // 과목명 (국어, 수학...)
}
