# python-app

Python 3.11+ / FastAPI + Uvicorn 기반 GitOps 샘플 애플리케이션

## 로컬 실행

```bash
pip install -r requirements.txt
APP_VERSION=1.0.0 APP_ENV=development uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 테스트 실행

```bash
pytest tests/ -v
```

## Docker 빌드 & 실행

```bash
docker build --build-arg APP_VERSION=1.0.0 -t python-app:local .
docker run -p 8000:8000 -e APP_VERSION=1.0.0 -e APP_ENV=development python-app:local
```

## 엔드포인트

| 경로 | 메서드 | 설명 |
|------|-------|------|
| `/` | GET | 앱 기본 정보 |
| `/health` | GET | 헬스체크 (K8s probe) |
