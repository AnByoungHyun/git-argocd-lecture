package com.example.javaapp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 앱 기본 API 컨트롤러
 * 예외 처리는 GlobalExceptionHandler(@RestControllerAdvice)가 담당
 */
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
    // GET /health — 헬스체크 (K8s liveness/readiness probe)
    // ----------------------------------------------------------------
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("app", "java-app");
        body.put("version", appVersion);
        return ResponseEntity.ok(body);
    }
}
