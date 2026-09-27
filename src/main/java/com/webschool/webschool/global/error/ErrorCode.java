package com.webschool.webschool.global.error;

import org.springframework.http.HttpStatus;

// 앱 전체에서 쓰는 에러 코드 - HTTP 상태와 기본 안내 문구를 한 곳에서 관리한다(2026-09-28 에러 처리
// 중앙화). 서비스에서 BusinessException(ErrorCode, [구체적인 메시지])로 던지면
// GlobalExceptionHandler가 상태 코드와 응답 형식을 알아서 맞춘다.
//
// 새 코드를 추가할 땐 "이 에러를 화면/클라이언트가 다르게 다뤄야 하는가"를 기준으로 할 것 - 문구만
// 다른 경우라면 새 코드 대신 기존 코드 + 구체적인 메시지로 충분하다.
public enum ErrorCode {

    // 400 - 사용자 입력/요청 내용이 규칙에 맞지 않음. 기존 IllegalArgumentException도 전부 여기로 매핑된다.
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    // 401/403 - 보통은 Spring Security가 먼저 처리하므로 서비스 계층에서 직접 쓸 일은 드물다.
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    // 404 - 대상이 없음(또는 블라인드/비공개처럼 존재 자체를 숨겨야 하는 경우).
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    // 409 - 이미 처리된 상태와 충돌(중복 신고 등).
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 너무 큽니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    // 5xx - 서버/외부 연동 문제. 사용자에게는 내부 사정을 노출하지 않는 문구만 보여준다.
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 응답에 실패했습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    // Spring MVC 내장 예외(파라미터 누락, 지원하지 않는 메서드 등)는 상태 코드만 알려주므로 그 상태에
    // 해당하는 대표 코드로 바꿔준다. 목록에 없는 상태는 4xx면 INVALID_INPUT, 그 외는 INTERNAL_ERROR.
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> FILE_TOO_LARGE;
            case 429 -> TOO_MANY_REQUESTS;
            default -> status >= 400 && status < 500 ? INVALID_INPUT : INTERNAL_ERROR;
        };
    }
}
