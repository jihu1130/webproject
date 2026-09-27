package com.webschool.webschool.global.error;

// JSON API의 에러 응답 본문. "error"는 예전부터 프론트 JS가 읽던 필드(body.error/data.error)라
// 이름을 바꾸면 안 된다 - code/status는 에러 처리 중앙화(2026-09-28) 때 추가된 필드.
public record ApiErrorResponse(String error, String code, int status) {

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(message, errorCode.name(), errorCode.getStatus().value());
    }
}
