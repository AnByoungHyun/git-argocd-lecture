# =============================================================
# Stage 1: Build — Maven + JDK 17
# =============================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 의존성 캐시 레이어 분리 (pom.xml만 먼저 복사)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 소스 복사 후 빌드 (테스트 스킵 — CI에서 별도 실행)
COPY src ./src
RUN mvn package -DskipTests -q

# =============================================================
# Stage 2: Extract layers (Spring Boot layertools)
# =============================================================
FROM eclipse-temurin:17-jre-alpine AS layers

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# =============================================================
# Stage 3: Runtime — JRE 17 slim (non-root)
# =============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# 보안: non-root 사용자 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 레이어 순서대로 복사 (캐시 최적화)
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

# 파일 소유권 변경
RUN chown -R appuser:appgroup /app

USER appuser

# 버전 주입 ARG/ENV
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION}
ENV APP_ENV=production

EXPOSE 8080

# JVM 메모리 설정 (limits 512Mi의 75% ≈ 384m)
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
