package com.webschool.webschool.global.logging;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// ErrorLogBuffer에 쌓이는 항목 하나 - DB에 저장하지 않는 메모리 전용 값이라 엔티티가 아니라
// 순수 POJO로 둔다.
@Getter
@Builder
public class ErrorLogEntry {
    private LocalDateTime timestamp;
    private String level;
    private String loggerName;
    private String message;
    private String stackTrace; // 예외 없이 ERROR로 로깅된 경우 null
}
