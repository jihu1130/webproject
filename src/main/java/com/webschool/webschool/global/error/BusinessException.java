package com.webschool.webschool.global.error;

// 서비스 계층이 "예상된 실패"(입력 오류, 대상 없음, 권한 없음 등)를 알릴 때 던지는 예외.
// ErrorCode로 HTTP 상태를 정하고, 화면에 보여줄 구체적인 메시지를 따로 줄 수 있다.
//
// IllegalArgumentException을 상속하는 이유: 이 프로젝트는 원래 모든 예상된 실패를
// IllegalArgumentException으로 던지고 컨트롤러가 catch (IllegalArgumentException e)로 받아 폼을
// 에러와 함께 다시 그리는 패턴이라, 서비스 코드를 BusinessException으로 하나씩 옮겨도 그 catch 블록들이
// 수정 없이 그대로 동작하게 하기 위함(점진적 이전).
public class BusinessException extends IllegalArgumentException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
