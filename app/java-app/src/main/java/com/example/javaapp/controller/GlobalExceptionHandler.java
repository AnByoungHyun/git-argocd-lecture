package com.example.javaapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 핸들러
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * DispatcherServlet 레벨에서 발생하는 404/405 예외를 포함한
 * 모든 컨트롤러 예외를 중앙에서 처리한다.
 *
 * 필수 application.yml 설정:
 *   spring.mvc.throw-exception-if-no-handler-found: true
 *   spring.web.resources.add-mappings: false
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ----------------------------------------------------------------
    // 404 — 존재하지 않는 경로
    // DispatcherServlet이 핸들러를 찾지 못할 때 발생
    // ----------------------------------------------------------------
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "Not Found", request.getRequestURI());
    }

    // ----------------------------------------------------------------
    // 405 — 허용되지 않는 메서드 (POST / 등)
    // 경로는 존재하지만 HTTP 메서드가 등록되지 않은 경우
    // ----------------------------------------------------------------
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", request.getRequestURI());
    }

    // ----------------------------------------------------------------
    // 500 — 그 외 예상치 못한 예외
    // ----------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternalError(
            Exception ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", request.getRequestURI());
    }

    // ----------------------------------------------------------------
    // 공통 에러 응답 빌더
    // 형식: {status: error, code: <HTTP코드>, message: <설명>, path: <경로>}
    // ----------------------------------------------------------------
    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("code", status.value());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
