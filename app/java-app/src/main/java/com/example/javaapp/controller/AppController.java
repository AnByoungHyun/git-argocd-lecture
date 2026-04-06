package com.example.javaapp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 앱 기본 API 컨트롤러
 * GET /      → HTML 웹 페이지 (브라우저 배포 확인용)
 * GET /api   → JSON 응답 (API 클라이언트용)
 * GET /health → JSON 헬스체크 (K8s probe용)
 * 예외 처리는 GlobalExceptionHandler(@RestControllerAdvice)가 담당
 */
@RestController
public class AppController {

    // 앱 시작 시간 (페이지 하단 표시용)
    private static final String START_TIME = Instant.now().toString();

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.environment:production}")
    private String appEnvironment;

    // ----------------------------------------------------------------
    // GET / — HTML 웹 페이지 (브라우저용, 배포 변경 시각화)
    // ----------------------------------------------------------------
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() {
        String html = String.format("""
            <!DOCTYPE html>
            <html lang="ko">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>java-app</title>
            <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{background:#FF6B35;color:#fff;font-family:system-ui,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center}
            .card{background:rgba(0,0,0,.2);border-radius:16px;padding:2.5rem 3rem;text-align:center;max-width:480px;width:90%%}
            h1{font-size:2.8rem;margin-bottom:.75rem}
            .version{display:inline-block;background:rgba(255,255,255,.25);border-radius:8px;padding:.3rem 1rem;font-size:1.3rem;font-weight:700;margin-bottom:1.5rem;letter-spacing:1px}
            .info{font-size:1rem;line-height:2.2;opacity:.95}
            .dot{color:#7fff7f;font-size:1.1rem}
            .footer{margin-top:1.5rem;font-size:.75rem;opacity:.6;border-top:1px solid rgba(255,255,255,.2);padding-top:.75rem}
            </style>
            </head>
            <body>
            <div class="card">
              <h1>&#9749; java-app</h1>
              <div class="version">v%s</div>
              <div class="info">
                <span class="dot">&#9679;</span> Running<br>
                Framework: Spring Boot 3.x<br>
                Port: 8080<br>
                Environment: %s
              </div>
              <div class="footer">Started: %s</div>
            </div>
            </body>
            </html>
            """,
            appVersion, appEnvironment, START_TIME
        );
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    // ----------------------------------------------------------------
    // GET /api — JSON 응답 (기존 GET / 응답 이동, API 클라이언트용)
    // ----------------------------------------------------------------
    @GetMapping("/api")
    public ResponseEntity<Map<String, Object>> api() {
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
