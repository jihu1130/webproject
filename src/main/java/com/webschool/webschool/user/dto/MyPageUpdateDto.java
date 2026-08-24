package com.webschool.webschool.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MyPageUpdateDto {
    private String username;
    private String nickname;
    private String email;
    private String newPassword;
    private String confirmNewPassword;
    private String schoolName;
    private String schoolCode;
    private String atptCode;
    private String schoolKind;
    private String grade;
    private String classNum;
    private String currentPassword;
}
