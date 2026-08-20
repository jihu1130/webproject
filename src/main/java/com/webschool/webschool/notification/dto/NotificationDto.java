package com.webschool.webschool.notification.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationDto {
    private Long id;
    private String type;
    private String typeLabel;
    private String message;
    private String link;
    private boolean read;
    private String createdAt;
}
