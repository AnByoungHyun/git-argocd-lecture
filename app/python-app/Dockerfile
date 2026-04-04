# =============================================================
# Stage 1: Dependencies — python:3.11-slim
# =============================================================
FROM python:3.11-slim AS deps

WORKDIR /install

# 시스템 패키지 최소화 후 pip 의존성 설치
COPY requirements.txt .
RUN pip install --upgrade pip --quiet && \
    pip install --no-cache-dir --prefix=/install/packages \
      -r requirements.txt

# =============================================================
# Stage 2: Runtime — python:3.11-slim (non-root)
# =============================================================
FROM python:3.11-slim AS runtime

# 보안: non-root 사용자 생성
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# wget (HEALTHCHECK용)
RUN apt-get update -q && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 의존성 복사
COPY --from=deps /install/packages /usr/local

# 소스 복사
COPY app ./app

# 파일 소유권 변경
RUN chown -R appuser:appgroup /app

USER appuser

# 버전 주입
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION}
ENV APP_ENV=production
ENV PORT=8000

EXPOSE 8000

CMD ["uvicorn", "app.main:app", \
     "--host", "0.0.0.0", \
     "--port", "8000", \
     "--workers", "1", \
     "--no-access-log"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8000/health || exit 1
