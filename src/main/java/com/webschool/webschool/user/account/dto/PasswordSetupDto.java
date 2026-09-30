package com.webschool.webschool.user.account.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PasswordSetupDto {
    private String newPassword;
    private String confirmNewPassword;
}
