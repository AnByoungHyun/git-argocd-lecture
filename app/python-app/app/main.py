"""
python-app — FastAPI 샘플 애플리케이션
GitOps CI/CD 파이프라인 검증용 Stateless REST API

엔드포인트:
  GET /       → HTML 웹 페이지 (브라우저용, 배포 변경 시각화)
  GET /api    → JSON 응답 (API 클라이언트용)
  GET /health → JSON 헬스체크 (K8s probe용)
"""

import os
from datetime import datetime, timezone
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import HTMLResponse, JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

# ----------------------------------------------------------------
# 앱 메타데이터
# ----------------------------------------------------------------
APP_NAME    = "python-app"
APP_VERSION = os.getenv("APP_VERSION", "1.0.0")
APP_ENV     = os.getenv("APP_ENV", "production")
PORT        = int(os.getenv("PORT", "8000"))

# 앱 시작 시간 (페이지 하단 표시용, 모듈 로드 시 1회 기록)
START_TIME  = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

# ----------------------------------------------------------------
# FastAPI 인스턴스
# ----------------------------------------------------------------
app = FastAPI(
    title=APP_NAME,
    version=APP_VERSION,
    docs_url=None,   # Swagger UI 비활성화 (경량화)
    redoc_url=None,  # ReDoc 비활성화
)


# ----------------------------------------------------------------
# 공통 에러 응답 생성 헬퍼
# ----------------------------------------------------------------
def error_response(status_code: int, message: str, path: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "status":  "error",
            "code":    status_code,
            "message": message,
            "path":    path,
        },
    )


# ----------------------------------------------------------------
# 전역 예외 핸들러 — 404 / 405 / 500
# ----------------------------------------------------------------
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    messages = {
        404: "Not Found",
        405: "Method Not Allowed",
        500: "Internal Server Error",
    }
    message = messages.get(exc.status_code, exc.detail or "Error")
    return error_response(exc.status_code, message, request.url.path)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    return error_response(422, "Unprocessable Entity", request.url.path)


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    return error_response(500, "Internal Server Error", request.url.path)


# ----------------------------------------------------------------
# GET / — HTML 웹 페이지 (브라우저용, 배포 변경 시각화)
# ----------------------------------------------------------------
@app.get("/", response_class=HTMLResponse)
async def root() -> HTMLResponse:
    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>python-app</title>
<style>
*{{margin:0;padding:0;box-sizing:border-box}}
body{{background:#306998;color:#fff;font-family:system-ui,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center}}
.card{{background:rgba(0,0,0,.2);border-radius:16px;padding:2.5rem 3rem;text-align:center;max-width:480px;width:90%}}
h1{{font-size:2.8rem;margin-bottom:.75rem}}
.version{{display:inline-block;background:rgba(255,255,255,.25);border-radius:8px;padding:.3rem 1rem;font-size:1.3rem;font-weight:700;margin-bottom:1.5rem;letter-spacing:1px}}
.info{{font-size:1rem;line-height:2.2;opacity:.95}}
.dot{{color:#7fff7f;font-size:1.1rem}}
.footer{{margin-top:1.5rem;font-size:.75rem;opacity:.6;border-top:1px solid rgba(255,255,255,.2);padding-top:.75rem}}
</style>
</head>
<body>
<div class="card">
  <h1>&#x1F40D; python-app</h1>
  <div class="version">v{APP_VERSION}</div>
  <div class="info">
    <span class="dot">&#9679;</span> Running<br>
    Framework: FastAPI<br>
    Port: {PORT}<br>
    Environment: {APP_ENV}
  </div>
  <div class="footer">Started: {START_TIME}</div>
</div>
</body>
</html>"""
    return HTMLResponse(content=html, status_code=200)


# ----------------------------------------------------------------
# GET /api — JSON 응답 (기존 GET / 응답 이동, API 클라이언트용)
# ----------------------------------------------------------------
@app.get("/api", response_class=JSONResponse)
async def api() -> dict[str, Any]:
    return {
        "app":         APP_NAME,
        "version":     APP_VERSION,
        "language":    "Python",
        "framework":   "FastAPI",
        "port":        PORT,
        "environment": APP_ENV,
    }


# ----------------------------------------------------------------
# GET /health — 헬스체크 (K8s liveness/readiness probe)
# ----------------------------------------------------------------
@app.get("/health", response_class=JSONResponse)
async def health() -> dict[str, Any]:
    return {
        "status":  "ok",
        "app":     APP_NAME,
        "version": APP_VERSION,
    }
