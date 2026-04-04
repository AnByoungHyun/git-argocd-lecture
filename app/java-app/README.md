# java-app

Java 17 / Spring Boot 3.x 기반 GitOps 샘플 애플리케이션

## 로컬 실행

```bash
# Maven 빌드
./mvnw package -DskipTests

# 실행
APP_VERSION=1.0.0 APP_ENV=development java -jar target/java-app-1.0.0.jar
```

## 테스트 실행

```bash
./mvnw test
```

## Docker 빌드 & 실행

```bash
docker build --build-arg APP_VERSION=1.0.0 -t java-app:local .
docker run -p 8080:8080 -e APP_VERSION=1.0.0 -e APP_ENV=development java-app:local
```

## 엔드포인트

| 경로 | 메서드 | 설명 |
|------|-------|------|
| `/` | GET | 앱 기본 정보 |
| `/health` | GET | 헬스체크 (K8s probe) |
