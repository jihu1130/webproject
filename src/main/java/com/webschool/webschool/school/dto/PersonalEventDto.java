package com.webschool.webschool.school.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersonalEventDto {
    private Long id;
    private String date; // yyyy-MM-dd
    private String title;
    private String memo;
    private boolean edited;
}
