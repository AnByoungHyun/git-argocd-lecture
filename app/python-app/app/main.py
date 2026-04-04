"""
python-app — FastAPI 샘플 애플리케이션
GitOps CI/CD 파이프라인 검증용 Stateless REST API
"""

import os
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

# ----------------------------------------------------------------
# 앱 메타데이터
# ----------------------------------------------------------------
APP_NAME    = "python-app"
APP_VERSION = os.getenv("APP_VERSION", "1.0.0")
APP_ENV     = os.getenv("APP_ENV", "production")
PORT        = int(os.getenv("PORT", "8000"))

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
# 전역 예외 핸들러 — 404 / 405
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
# GET / — 앱 기본 정보
# ----------------------------------------------------------------
@app.get("/", response_class=JSONResponse)
async def root() -> dict[str, Any]:
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
