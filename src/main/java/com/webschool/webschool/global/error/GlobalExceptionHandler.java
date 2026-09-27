package com.webschool.webschool.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

// 전역 예외 처리(2026-09-28 에러 처리 중앙화). 예전엔 8개 컨트롤러가 똑같은
// @ExceptionHandler(IllegalArgumentException → JSON 400)를 각자 복사해 갖고 있었고, 그게 없는
// 컨트롤러에서 난 같은 입력 오류는 500으로 떨어졌다. 이제 모든 컨트롤러가 여기 한 곳을 거친다.
//
// 응답 형식은 "예외가 난 핸들러가 원래 무엇을 돌려주는가"로 정한다:
// - JSON API(@ResponseBody/@RestController/ResponseEntity 반환) → ApiErrorResponse JSON
//   ({"error": 메시지, "code": ..., "status": ...} - error 필드는 기존 프론트 호환용)
// - 화면(템플릿 이름/redirect 반환) → response.sendError로 Spring Boot 기본 /error 포워드
//   → templates/error.html (4xx면 구체적인 메시지를 errorMessage 요청 속성으로 함께 넘김)
//
// 컨트롤러가 catch (IllegalArgumentException e)로 직접 잡아 폼을 다시 그리는 곳은 지금처럼 그대로
// 동작한다 - 여기는 컨트롤러가 잡지 않고 흘려보낸 예외만 받는다.
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    public static final String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";

    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        return respond(e.getErrorCode(), e.getMessage(), request, response);
    }

    // 기존 서비스 코드가 던지는 IllegalArgumentException - 전부 사용자에게 보여줄 수 있는 문구로
    // 작성돼 있으므로(예: "제목을 입력해주세요.") 메시지를 그대로 400으로 내려준다.
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request,
                                        HttpServletResponse response) throws IOException {
        return respond(ErrorCode.INVALID_INPUT, e.getMessage(), request, response);
    }

    // 요청 값 자체가 형식에 안 맞는 경우(숫자 자리에 문자 등) - Spring MVC 내장 예외지만 아래
    // ErrorResponse를 구현하지 않아 따로 잡는다(안 잡으면 500으로 떨어짐 - Spring 기본 처리
    // (DefaultHandlerExceptionResolver)도 이 셋은 400으로 응답한다).
    @ExceptionHandler({TypeMismatchException.class, HttpMessageNotReadableException.class, BindException.class})
    public Object handleMalformedRequest(Exception e, HttpServletRequest request,
                                         HttpServletResponse response) throws IOException {
        log.debug("Malformed request {} {}: {}", request.getMethod(), request.getRequestURI(), e.toString());
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getDefaultMessage(), request, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        return respond(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage(), request, response);
    }

    @ExceptionHandler(Exception.class)
    public Object handleOthers(Exception e, HttpServletRequest request,
                               HttpServletResponse response) throws Exception {
        // 로그인/권한 예외는 Spring Security(ExceptionTranslationFilter)가 로그인 페이지 리다이렉트나
        // 403 처리를 해야 하므로 여기서 삼키지 않고 그대로 다시 던진다.
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        // 클라이언트가 응답 중간에 연결을 끊은 경우 - 보낼 곳이 없으니 조용히 끝낸다.
        if (e instanceof AsyncRequestNotUsableException || isClientAbort(e)) {
            log.debug("Client aborted: {} {}", request.getMethod(), request.getRequestURI());
            return null;
        }
        // Spring MVC 내장 예외(필수 파라미터 누락/타입 불일치 400, 없는 경로 404, 지원 안 하는 메서드
        // 405 등)는 ErrorResponse로 상태를 알려준다 - 사용자 요청 쪽 문제라 ERROR 로그는 남기지 않는다.
        if (e instanceof ErrorResponse errorResponse) {
            ErrorCode code = ErrorCode.fromStatus(errorResponse.getStatusCode().value());
            log.debug("Request error {} {}: {}", request.getMethod(), request.getRequestURI(), e.toString());
            return respond(code, code.getDefaultMessage(), request, response);
        }
        // 그 외는 진짜 서버 오류 - 반드시 ERROR 로그(스택 포함)로 남긴다. CloudWatch 에러 로그 알람
        // (webschool-app-errors)과 관리자 에러 로그 화면(InMemoryErrorAppender)이 이 로그를 기준으로 동작한다.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, response);
    }

    private Object respond(ErrorCode code, String message, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        String safeMessage = (message == null || message.isBlank()) ? code.getDefaultMessage() : message;
        if (isJsonHandler(request)) {
            return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code, safeMessage));
        }
        // 화면 요청 - 기존과 똑같이 Spring Boot의 /error 포워드로 error.html을 그린다(네비바 등 공통
        // 레이아웃이 기존 에러 화면과 동일하게 나오도록). 5xx는 내부 사정을 노출하지 않도록 메시지를 안 넘긴다.
        if (code.getStatus().is4xxClientError()) {
            request.setAttribute(ERROR_MESSAGE_ATTRIBUTE, safeMessage);
        }
        response.sendError(code.getStatus().value());
        return null;
    }

    // 예외가 난 컨트롤러 메서드는 HandlerMapping이 요청 속성에 남겨둔 값으로 찾는다(@ExceptionHandler의
    // HandlerMethod 파라미터는 컨트롤러가 아닌 핸들러 - 정적 리소스 등 - 에서 난 예외일 때 채울 수 없어
    // 핸들러 호출 자체가 실패하므로 쓰지 않음). 컨트롤러 메서드가 아니면 요청 헤더/경로로 판단한다.
    private boolean isJsonHandler(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            if (handlerMethod.hasMethodAnnotation(ResponseBody.class)
                    || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ResponseBody.class)) {
                return true;
            }
            // @ResponseBody 없이 ResponseEntity를 돌려주는 핸들러도 본문을 직접 쓰는 API다.
            return HttpEntity.class.isAssignableFrom(handlerMethod.getMethod().getReturnType());
        }
        String accept = request.getHeader("Accept");
        boolean prefersJson = accept != null && accept.contains("application/json") && !accept.contains("text/html");
        return prefersJson || request.getRequestURI().contains("/api/");
    }

    private boolean isClientAbort(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getClass().getSimpleName().equals("ClientAbortException")) {
                return true;
            }
        }
        return false;
    }
}
