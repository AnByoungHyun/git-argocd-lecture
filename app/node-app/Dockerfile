# =============================================================
# Stage 1: Dependencies — node:18-alpine
# =============================================================
FROM node:18-alpine AS deps

WORKDIR /app

# 의존성 캐시 레이어 분리
COPY package*.json ./
RUN npm ci --omit=dev --ignore-scripts

# =============================================================
# Stage 2: Runtime — node:18-alpine (non-root)
# =============================================================
FROM node:18-alpine AS runtime

# 보안: node 사용자는 alpine 이미지에 기본 포함
# UID 1000으로 node 사용자 활성화
RUN apk add --no-cache wget

WORKDIR /app

# 프로덕션 의존성 복사
COPY --from=deps /app/node_modules ./node_modules

# 소스 복사
COPY src ./src
COPY package.json ./

# 파일 소유권 변경 (node 사용자: UID 1000)
RUN chown -R node:node /app

USER node

# 버전 주입
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION}
ENV APP_ENV=production
ENV PORT=3000
ENV NODE_ENV=production

EXPOSE 3000

CMD ["node", "src/index.js"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:3000/health || exit 1
