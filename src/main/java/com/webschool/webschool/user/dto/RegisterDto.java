package com.webschool.webschool.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterDto {
    private String username;
    private String password;
    private String confirmPassword;
    private String nickname;
    private String schoolName;
    private String schoolCode;
    private String atptCode;
    private String schoolKind;
    private String grade;
    private String classNum;
}