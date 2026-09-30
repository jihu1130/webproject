package com.webschool.webschool.user.account.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SchoolSetupDto {
    private String schoolName;
    private String schoolCode;
    private String atptCode;
    private String schoolKind;
    private String grade;
    private String classNum;
}
