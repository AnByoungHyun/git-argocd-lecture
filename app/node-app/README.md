# node-app

Node.js 18+ / Express 기반 GitOps 샘플 애플리케이션

## 로컬 실행

```bash
npm install
npm start
```

## 테스트 실행

```bash
npm test
```

## Docker 빌드 & 실행

```bash
docker build --build-arg APP_VERSION=1.0.0 -t node-app:local .
docker run -p 3000:3000 -e APP_VERSION=1.0.0 -e APP_ENV=development node-app:local
```

## 엔드포인트

| 경로 | 메서드 | 설명 |
|------|-------|------|
| `/` | GET | 앱 기본 정보 |
| `/health` | GET | 헬스체크 (K8s probe) |
