package com.example.javaapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AppController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.environment:production}")
    private String appEnvironment;

    // ----------------------------------------------------------------
    // GET / — 앱 기본 정보
    // ----------------------------------------------------------------
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> index() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "java-app");
        body.put("version", appVersion);
        body.put("language", "Java");
        body.put("framework", "Spring Boot 3.x");
        body.put("port", 8080);
        body.put("environment", appEnvironment);
        return ResponseEntity.ok(body);
    }

    // ----------------------------------------------------------------
    // GET /health — 헬스체크
    // ----------------------------------------------------------------
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("app", "java-app");
        body.put("version", appVersion);
        return ResponseEntity.ok(body);
    }

    // ----------------------------------------------------------------
    // 404 — 존재하지 않는 경로
    // ----------------------------------------------------------------
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "Not Found", request.getRequestURI());
    }

    // ----------------------------------------------------------------
    // 405 — 허용되지 않는 메서드
    // ----------------------------------------------------------------
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", request.getRequestURI());
    }

    // ----------------------------------------------------------------
    // 500 — 공통 에러 핸들러
    // ----------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternalError(
            Exception ex, HttpServletRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("code", status.value());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
