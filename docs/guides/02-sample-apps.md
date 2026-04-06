# 02. 샘플 앱 구조 이해 + 로컬 실행

> 가이드 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 상태: ✅ 실습 단계 작성 완료  
> 이전 가이드: [01. 사전 준비](01-prerequisites.md) | 다음 가이드: [03. Dockerfile 작성](03-dockerize.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] 3개 샘플 앱(Java/Node.js/Python)의 디렉토리 구조와 핵심 코드를 설명할 수 있다
- [ ] 각 앱의 `GET /`(HTML), `GET /api`(JSON), `GET /health`(JSON) 엔드포인트를 이해한다
- [ ] 각 앱을 로컬에서 직접 실행하고 HTML 페이지와 API 응답을 확인할 수 있다
- [ ] 단위 테스트를 실행하고 전체 통과를 확인할 수 있다

---

## 📖 이론

### 🧩 프레임워크 비교 — Spring Boot vs Express vs FastAPI

이 프로젝트는 동일한 역할(REST API 서버)을 3가지 언어·프레임워크로 구현합니다.  
각각의 특징을 이해하면 CI/CD 파이프라인이 왜 언어별로 다르게 구성되는지 파악할 수 있습니다.

| 항목 | Spring Boot (Java) | Express (Node.js) | FastAPI (Python) |
|------|-------------------|------------------|-----------------|
| **언어** | Java 17 | Node.js 18+ | Python 3.11+ |
| **실행 모델** | 멀티스레드 (JVM) | 단일스레드 이벤트루프 | 비동기 (asyncio) |
| **기동 속도** | 느림 (JVM 워밍업 10~30초) | 빠름 (1~3초) | 빠름 (1~3초) |
| **빌드 산출물** | JAR 파일 (Maven/Gradle) | 없음 (소스 직접 실행) | 없음 (소스 직접 실행) |
| **의존성 관리** | `pom.xml` → Maven | `package.json` → npm | `requirements.txt` → pip |
| **포트** | 8080 | 3000 | 8000 |
| **Docker 빌드** | 멀티스테이지 필수 (빌드 분리) | 2단계 (의존성 분리) | 2단계 (의존성 분리) |

> 💡 **CI 파이프라인 차이**: Java는 빌드 단계(mvn package)가 추가되고, Node/Python은 의존성 설치 후 바로 실행됩니다.  
> K8s에서는 Java 앱의 `readinessProbe` 초기 지연을 더 길게(30초) 설정하는 이유도 JVM 기동 시간 때문입니다.

---

### 🌐 REST API 기본 개념

**REST(Representational State Transfer)**는 HTTP를 이용해 자원을 주고받는 API 설계 방식입니다.

#### 엔드포인트(Endpoint)

URL + HTTP 메서드의 조합이 하나의 엔드포인트를 정의합니다.

```
GET  http://localhost:8080/        ← 앱 기본 정보 조회
GET  http://localhost:8080/health  ← 헬스체크
POST http://localhost:8080/        ← 405 (등록되지 않은 메서드)
GET  http://localhost:8080/nopath  ← 404 (존재하지 않는 경로)
```

#### HTTP 상태 코드

서버가 요청을 처리한 결과를 숫자로 표현합니다.

| 코드 | 의미 | 이 프로젝트에서의 사용 |
|------|------|----------------------|
| `200 OK` | 정상 처리 | `GET /`, `GET /health` |
| `404 Not Found` | 경로 없음 | 등록되지 않은 URL 요청 |
| `405 Method Not Allowed` | 메서드 불일치 | 경로는 있지만 POST 등 미지원 메서드 |
| `500 Internal Server Error` | 서버 내부 오류 | 예상치 못한 예외 발생 시 |

#### JSON 응답 형식

모든 응답은 `Content-Type: application/json`으로 반환됩니다.

```json
// 정상 응답 (GET /api)
{
  "app": "java-app",
  "version": "1.0.0",
  "language": "Java",
  "framework": "Spring Boot 3.x",
  "port": 8080,
  "environment": "production"
}

// 에러 응답 (404, 405, 500 공통 형식)
{
  "status": "error",
  "code": 404,
  "message": "Not Found",
  "path": "/unknown"
}
```

> 💡 **공통 형식의 중요성**: 에러 응답을 일정한 형식으로 통일하면, API를 사용하는 클라이언트·모니터링 도구·CI 테스트 코드가 에러를 일관되게 처리할 수 있습니다.

---

### 💓 헬스체크 엔드포인트의 역할

`GET /health`는 단순해 보이지만, **컨테이너 오케스트레이션 환경에서 핵심 역할**을 합니다.

#### K8s Probe 연동 구조

```
┌────────────────────────────────────────────────────┐
│                  Kubernetes                        │
│                                                    │
│  kubelet ──────► livenessProbe  ──► GET /health   │
│                  (살아있나?)                         │
│                   ↓ 실패 3회 → 컨테이너 재시작       │
│                                                    │
│  kubelet ──────► readinessProbe ──► GET /health   │
│                  (트래픽 받을 준비됐나?)              │
│                   ↓ 실패 → Service 엔드포인트 제거   │
└────────────────────────────────────────────────────┘
```

| Probe 종류 | 역할 | 실패 시 동작 | 초기 지연 |
|-----------|------|------------|---------|
| **livenessProbe** | 앱이 살아있는지 확인 | 컨테이너 재시작 | Java: 30s / Node·Python: 10s |
| **readinessProbe** | 트래픽 수신 가능 여부 | Service에서 제외 (트래픽 차단) | Java: 30s / Node·Python: 10s |

#### 이 프로젝트의 /health 응답

```json
{ "status": "ok", "app": "java-app", "version": "1.0.0" }
```

- `status: "ok"` → HTTP 200 반환 → Probe 성공
- 비정상 시 HTTP 500 반환 → Probe 실패 → K8s가 컨테이너 재시작/격리

> 💡 **왜 /health를 별도로 만드는가?**: `GET /`는 비즈니스 로직을 실행하지만, `/health`는 외부 의존성(DB, 캐시 등) 연결 상태만 빠르게 확인하는 용도입니다. 샘플 앱은 Stateless라 동일하게 구현되어 있지만, 실제 서비스에서는 차이가 납니다.

---

### 🛡️ 에러 핸들링 설계 — GlobalExceptionHandler 분리 이유

#### 문제: 컨트롤러 내부 @ExceptionHandler의 한계

```
HTTP 요청
    │
    ▼
DispatcherServlet
    │
    ├─ 경로 매핑 실패 → NoHandlerFoundException (404)  ←─┐ 컨트롤러에
    ├─ 메서드 불일치 → HttpRequestMethodNotSupportedException (405)  ←─┤ 도달하지 않음
    │                                                              │
    ▼                                                              │
AppController                                                      │
    └─ @ExceptionHandler (컨트롤러 내부)  ─────────────── 여기서 잡을 수 없음 ─┘
```

**핵심 문제**: `@RestController` 내부의 `@ExceptionHandler`는 해당 컨트롤러의 핸들러 메서드 실행 중 발생한 예외만 처리합니다. 404/405는 그 이전 단계(DispatcherServlet)에서 발생합니다.

#### 해결: @RestControllerAdvice (GlobalExceptionHandler)

```
HTTP 요청
    │
    ▼
DispatcherServlet
    │
    ├─ NoHandlerFoundException ────────────────────────────┐
    ├─ HttpRequestMethodNotSupportedException ─────────────┤
    │                                                      ▼
    ▼                                          GlobalExceptionHandler
AppController                                  (@RestControllerAdvice)
    └─ 비즈니스 로직 예외 ────────────────────────────────►│
                                                           │
                                               에러 응답 통일 반환
```

`@RestControllerAdvice`는 **애플리케이션 전체 범위**에서 예외를 가로채므로, DispatcherServlet 레벨 예외도 처리 가능합니다.

> 💡 **404 처리 필수 조건**: `application.yml`에 다음 두 줄이 반드시 있어야 합니다.
> ```yaml
> spring:
>   mvc:
>     throw-exception-if-no-handler-found: true  # 404를 예외로 throw
>   web:
>     resources:
>       add-mappings: false  # 정적 리소스 핸들러 비활성화 (이것 없으면 404가 예외로 안 됨)
> ```

---

### ⚙️ 환경변수를 통한 설정 주입 — 12-Factor App 원칙

**12-Factor App**은 현대 클라우드 네이티브 앱을 개발하는 모범 사례 집합입니다.  
그 중 **Factor III: Config** 원칙은 *"설정은 환경변수로 주입하라"*고 정의합니다.

#### 왜 환경변수인가?

```
❌ 잘못된 방식: 코드에 버전 하드코딩
  String version = "1.0.0";  // 빌드할 때마다 코드 수정 필요

✅ 올바른 방식: 환경변수로 주입
  String version = System.getenv("APP_VERSION");  // 실행 환경에서 결정
```

| 장점 | 설명 |
|------|------|
| **환경 독립성** | 개발/스테이징/프로덕션에서 동일 이미지, 다른 설정 사용 가능 |
| **보안** | 시크릿(DB 비밀번호 등)을 코드/이미지에 포함하지 않음 |
| **GitOps 호환** | K8s Deployment의 `env` 필드로 주입 → Git으로 관리 |
| **CI/CD 연동** | CI에서 git SHA를 `APP_VERSION`으로 주입해 버전 추적 |

#### 이 프로젝트의 환경변수 흐름

```
GitHub Actions (CI)
    │
    │  --build-arg APP_VERSION=$GITHUB_SHA
    ▼
Docker 빌드 (Dockerfile)
    ARG APP_VERSION=1.0.0    ← 기본값
    ENV APP_VERSION=${APP_VERSION}  ← ARG를 ENV로 전파
    │
    ▼
실행 중인 컨테이너
    │  process.env.APP_VERSION (Node.js)
    │  os.getenv("APP_VERSION") (Python)
    │  ${APP_VERSION:1.0.0}    (Spring Boot/YAML)
    ▼
API 응답 "version": "a1b2c3d"  ← git SHA 반영
```

#### K8s에서의 주입 방식 (가이드 05에서 상세 설명)

```yaml
# Deployment.yaml
env:
  - name: APP_VERSION
    value: "a1b2c3d"      # CI가 git SHA로 자동 치환
  - name: APP_ENV
    value: "production"
```

---

### 프로젝트 전체 구조

```
git-argocd-lecture/
├── app/
│   ├── java-app/          # Java 17 / Spring Boot 3.x  (포트: 8080)
│   │   ├── src/
│   │   │   ├── main/java/com/example/javaapp/
│   │   │   │   ├── JavaAppApplication.java       ← 앱 진입점
│   │   │   │   └── controller/
│   │   │   │       ├── AppController.java         ← GET / , GET /health
│   │   │   │       └── GlobalExceptionHandler.java← 404/405/500 처리
│   │   │   └── resources/application.yml          ← 포트, 버전 설정
│   │   ├── pom.xml                                ← Maven 빌드 설정
│   │   ├── mvnw / mvnw.cmd                        ← Maven Wrapper (실행파일)
│   │   └── Dockerfile                             ← 멀티스테이지 빌드
│   │
│   ├── node-app/          # Node.js 18+ / Express  (포트: 3000)
│   │   ├── src/
│   │   │   ├── app.js     ← Express 라우터 (GET /, GET /health)
│   │   │   └── index.js   ← 서버 진입점, Graceful Shutdown
│   │   ├── test/app.test.js
│   │   ├── package.json   ← 의존성 정의 (Express)
│   │   ├── package-lock.json ← 의존성 잠금 파일 (npm ci 필수)
│   │   └── Dockerfile
│   │
│   └── python-app/        # Python 3.11+ / FastAPI  (포트: 8000)
│       ├── app/
│       │   ├── __init__.py
│       │   └── main.py    ← FastAPI 앱, 라우터, 에러 핸들러
│       ├── tests/
│       │   └── test_main.py
│       ├── requirements.txt ← 의존성 (FastAPI, Uvicorn, pytest)
│       └── Dockerfile
│
├── .github/workflows/     # GitHub Actions CI
├── manifests/             # K8s 매니페스트
├── argocd/                # ArgoCD Application CR
└── docs/                  # 가이드 문서
```

### API 명세 요약

모든 앱은 동일한 엔드포인트 구조를 따릅니다:

| 엔드포인트 | 응답 형식 | 상태코드 | 내용 |
|----------|---------|---------|------|
| `GET /` | `text/html` | 200 | 배경색 HTML 페이지 (앱명·버전·상태 시각화) |
| `GET /api` | `application/json` | 200 | `app`, `version`, `language`, `framework`, `port`, `environment` |
| `GET /health` | `application/json` | 200 | `status`, `app`, `version` |
| 잘못된 경로 | `application/json` | 404 | `status`, `code`, `message`, `path` |
| 잘못된 메서드 | `application/json` | 405 | `status`, `code`, `message`, `path` |

> 💡 **GET / vs GET /api**: 브라우저에서는 `GET /`(HTML 페이지)로, 자동화·테스트에서는 `GET /api`(JSON)로 접근합니다.
> 앱별 배경색: ☕ java-app `#FF6B35` (주황) | 🟢 node-app `#68A063` (초록) | 🐍 python-app `#306998` (파랑)

### java-app 핵심 코드 포인트

**`AppController.java`** — 비즈니스 로직 담당

```java
@RestController
public class AppController {

    @Value("${app.version:1.0.0}")       // application.yml → APP_VERSION 환경변수로 오버라이드
    private String appVersion;

    @GetMapping("/")
    public ResponseEntity<String> index() { ... }  // HTML 반환

    @GetMapping("/api")
    public ResponseEntity<Map<String, Object>> api() { ... }  // JSON 반환

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() { ... }
}
```

**`GlobalExceptionHandler.java`** — 404/405/500 에러 처리 전담

```java
@RestControllerAdvice  // 모든 컨트롤러의 예외를 중앙에서 처리
public class GlobalExceptionHandler {
    @ExceptionHandler(NoHandlerFoundException.class)        // → 404
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) // → 405
    @ExceptionHandler(Exception.class)                       // → 500
}
```

> 💡 **설계 포인트**: 에러 처리 로직을 컨트롤러에서 분리해 `GlobalExceptionHandler`에 위임합니다.  
> `application.yml`의 `spring.mvc.throw-exception-if-no-handler-found: true` 설정이 필수입니다.

**`application.yml`** — 핵심 설정

```yaml
server:
  port: 8080

app:
  version: ${APP_VERSION:1.0.0}      # 환경변수 APP_VERSION → 없으면 1.0.0
  environment: ${APP_ENV:production}
```

### node-app 핵심 코드 포인트

**`src/app.js`** — Express 라우터 및 에러 핸들러

```javascript
const APP_VERSION = process.env.APP_VERSION || '1.0.0';  // 환경변수로 버전 주입

app.get('/', (req, res) => { res.status(200).json({ app: 'node-app', ... }) });
app.get('/health', (req, res) => { res.status(200).json({ status: 'ok', ... }) });
app.use((req, res) => { res.status(404).json({ status: 'error', ... }) });  // 404
```

**`src/index.js`** — 서버 진입점

```javascript
const server = app.listen(PORT, '0.0.0.0', () => { ... });
process.on('SIGTERM', () => { server.close(...) });  // Graceful Shutdown
```

### python-app 핵심 코드 포인트

**`app/main.py`** — FastAPI 앱

```python
APP_VERSION = os.getenv("APP_VERSION", "1.0.0")  # 환경변수로 버전 주입

@app.get("/")
async def root() -> dict: return { "app": "python-app", ... }

@app.get("/health")
async def health() -> dict: return { "status": "ok", ... }

@app.exception_handler(StarletteHTTPException)  # 404, 405 처리
async def http_exception_handler(...): ...
```

---

## 🛠️ 실습 단계

> ⚠️ **사전 조건**: [01. 사전 준비](01-prerequisites.md)가 완료된 상태여야 합니다.

---

### Step 1: 저장소 진입 및 구조 확인

```bash
# 저장소 루트로 이동
cd ~/workspace/git-argocd-lecture

# 앱 디렉토리 구조 확인
find app/ -type f | sort
```

```
예상 출력:
app/java-app/Dockerfile
app/java-app/mvnw
app/java-app/pom.xml
app/java-app/src/main/java/com/example/javaapp/JavaAppApplication.java
app/java-app/src/main/java/com/example/javaapp/controller/AppController.java
app/java-app/src/main/java/com/example/javaapp/controller/GlobalExceptionHandler.java
app/java-app/src/main/resources/application.yml
app/java-app/src/test/java/com/example/javaapp/controller/AppControllerTest.java
app/node-app/Dockerfile
app/node-app/package.json
app/node-app/package-lock.json
app/node-app/src/app.js
app/node-app/src/index.js
app/node-app/test/app.test.js
app/python-app/Dockerfile
app/python-app/app/main.py
app/python-app/requirements.txt
app/python-app/tests/test_main.py
```

✅ **확인**: 모든 파일이 존재하면 성공 (특히 `mvnw`, `package-lock.json` 포함 여부 확인)

---

### Step 2: java-app 로컬 실행

Java 17이 설치된 환경에서 Maven Wrapper(`mvnw`)를 사용해 Spring Boot 앱을 실행합니다.

**2-1. 의존성 다운로드 + 빌드 + 실행**

```bash
cd ~/workspace/git-argocd-lecture/app/java-app

# Maven Wrapper 실행 권한 확인 (없으면 설정)
chmod +x mvnw

# Spring Boot 실행 (처음 실행 시 Maven 의존성 다운로드로 1~3분 소요)
./mvnw spring-boot:run
```

```
예상 출력 (하단 로그 확인):
[INFO] Scanning for projects...
[INFO] Building java-app 1.0.0
...
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v3.2.4)

...
Started JavaAppApplication in 2.xxx seconds
Tomcat started on port 8080 (http)
```

**2-2. 브라우저 + API 응답 확인** (새 터미널에서 실행)

```bash
# GET / — 브라우저로 HTML 페이지 확인 (주황 배경색 #FF6B35)
open http://localhost:8080/

# GET /api — JSON 앱 정보 확인
curl -s http://localhost:8080/api | python3 -m json.tool

# GET /health — 헬스체크
curl -s http://localhost:8080/health | python3 -m json.tool
```

```
예상 출력 (GET /api):
{
    "app": "java-app",
    "version": "1.0.0",
    "language": "Java",
    "framework": "Spring Boot 3.x",
    "port": 8080,
    "environment": "production"
}

예상 출력 (GET /health):
{
    "status": "ok",
    "app": "java-app",
    "version": "1.0.0"
}

브라우저 (GET /):
  - 배경색: 주황 (#FF6B35)
  - ☕ java-app, Version: 1.0.0, ● Running
```

**2-3. 에러 핸들링 확인**

```bash
# 404 — 존재하지 않는 경로
curl -s http://localhost:8080/unknown | python3 -m json.tool

# 405 — 허용되지 않는 메서드 (POST)
curl -s -X POST http://localhost:8080/ | python3 -m json.tool
```

```
예상 출력 (404):
{
    "status": "error",
    "code": 404,
    "message": "Not Found",
    "path": "/unknown"
}
```

**2-4. 앱 종료**

```bash
# 실행 중인 터미널에서 Ctrl+C
```

✅ **확인**: 브라우저 주황 배경 HTML 페이지, `GET /api` JSON 응답, `GET /health` `"status": "ok"` 확인

---

### Step 3: node-app 로컬 실행

Node.js 18+ 환경에서 Express 앱을 실행합니다.

**3-1. 의존성 설치 + 실행**

```bash
cd ~/workspace/git-argocd-lecture/app/node-app

# 의존성 설치 (package-lock.json 기반 재현 설치)
npm ci

# 앱 실행
npm start
```

```
예상 출력:
> node-app@1.0.0 start
> node src/index.js

[node-app] listening on port 3000
[node-app] version: 1.0.0
[node-app] environment: production
```

**3-2. 브라우저 + API 응답 확인** (새 터미널에서 실행)

```bash
# GET / — 브라우저로 HTML 페이지 확인 (초록 배경색 #68A063)
open http://localhost:3000/

# GET /api — JSON 앱 정보 확인
curl -s http://localhost:3000/api | python3 -m json.tool

# GET /health — 헬스체크
curl -s http://localhost:3000/health | python3 -m json.tool

# 404 — 존재하지 않는 경로
curl -s http://localhost:3000/no-such-path | python3 -m json.tool
```

```
예상 출력 (GET /api):
{
    "app": "node-app",
    "version": "1.0.0",
    "language": "Node.js",
    "framework": "Express",
    "port": 3000,
    "environment": "production"
}

예상 출력 (GET /health):
{
    "status": "ok",
    "app": "node-app",
    "version": "1.0.0"
}

브라우저 (GET /):
  - 배경색: 초록 (#68A063)
  - 🟢 node-app, Version: 1.0.0, ● Running
```

**3-3. 앱 종료**

```bash
# Ctrl+C (Graceful Shutdown — SIGINT 처리)
```

```
예상 출력:
[node-app] SIGINT received — shutting down gracefully
[node-app] server closed
```

✅ **확인**: 브라우저 초록 배경 HTML 페이지, `GET /api` JSON 응답 (`"app": "node-app"`), `GET /health` 확인

---

### Step 4: python-app 로컬 실행

Python 3.11+ 환경에서 FastAPI + Uvicorn 앱을 실행합니다.

**4-1. 가상환경 생성 + 의존성 설치 + 실행**

```bash
cd ~/workspace/git-argocd-lecture/app/python-app

# 가상환경 생성 (프로젝트 격리)
python3 -m venv .venv

# 가상환경 활성화
source .venv/bin/activate

# 의존성 설치 (FastAPI, Uvicorn, pytest 등)
pip install -r requirements.txt

# 앱 실행
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

```
예상 출력:
INFO:     Will watch for changes in these directories: ['/path/to/python-app']
INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
INFO:     Started reloader process [xxxxx] using WatchFiles
INFO:     Started server process [xxxxx]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
```

**4-2. 브라우저 + API 응답 확인** (새 터미널에서 실행)

```bash
# 가상환경 재활성화 (새 터미널)
cd ~/workspace/git-argocd-lecture/app/python-app
source .venv/bin/activate

# GET / — 브라우저로 HTML 페이지 확인 (파랑 배경색 #306998)
open http://localhost:8000/

# GET /api — JSON 앱 정보 확인
curl -s http://localhost:8000/api | python3 -m json.tool

# GET /health — 헬스체크
curl -s http://localhost:8000/health | python3 -m json.tool

# 404 — 존재하지 않는 경로
curl -s http://localhost:8000/no-such-path | python3 -m json.tool
```

```
예상 출력 (GET /api):
{
    "app": "python-app",
    "version": "1.0.0",
    "language": "Python",
    "framework": "FastAPI",
    "port": 8000,
    "environment": "production"
}

예상 출력 (GET /health):
{
    "status": "ok",
    "app": "python-app",
    "version": "1.0.0"
}

브라우저 (GET /):
  - 배경색: 파랑 (#306998)
  - 🐍 python-app, Version: 1.0.0, ● Running
```

**4-3. 앱 종료 및 가상환경 비활성화**

```bash
# Ctrl+C로 종료 후
deactivate
```

✅ **확인**: 브라우저 파랑 배경 HTML 페이지, `GET /api` JSON 응답 (`"app": "python-app"`), `GET /health` 확인

---

### Step 5: 단위 테스트 실행

각 앱의 단위 테스트를 실행해 모든 테스트가 통과하는지 확인합니다.

**5-1. java-app 단위 테스트**

```bash
cd ~/workspace/git-argocd-lecture/app/java-app

# Maven 테스트 실행 (JUnit 5 기반, 4개 테스트)
./mvnw test
```

```
예상 출력:
[INFO] Running com.example.javaapp.controller.AppControllerTest
...
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
[INFO] Total time: xx.xxx s
```

> 💡 **테스트 항목**: `GET /` 200 응답, `GET /health` status=ok, POST / → 405, GET /unknown → 404

**5-2. node-app 단위 테스트**

```bash
cd ~/workspace/git-argocd-lecture/app/node-app

# Node.js 내장 테스트 러너 사용 (supertest 기반, 4개 테스트)
npm test
```

```
예상 출력:
> node-app@1.0.0 test
> node --test test/

▶ GET /
  ✔ should return 200 with app info (xxxms)
  ✔ should return 405 for POST / (xxxms)
▶ GET /health
  ✔ should return 200 with status ok (xxxms)
  ✔ should return 404 for unknown path (xxxms)

ℹ tests 4
ℹ pass 4
ℹ fail 0
```

**5-3. python-app 단위 테스트**

```bash
cd ~/workspace/git-argocd-lecture/app/python-app

# 가상환경 활성화 (비활성화 상태인 경우)
source .venv/bin/activate

# pytest 실행 (9개 테스트)
pytest tests/ -v
```

```
예상 출력:
======================== test session starts ========================
platform darwin -- Python 3.11.x, pytest-8.1.1
collected 9 items

tests/test_main.py::TestRoot::test_get_root_returns_200         PASSED
tests/test_main.py::TestRoot::test_get_root_content_type_is_json PASSED
tests/test_main.py::TestRoot::test_get_root_response_fields     PASSED
tests/test_main.py::TestRoot::test_post_root_returns_405        PASSED
tests/test_main.py::TestHealth::test_get_health_returns_200     PASSED
tests/test_main.py::TestHealth::test_get_health_status_is_ok    PASSED
tests/test_main.py::TestHealth::test_post_health_returns_405    PASSED
tests/test_main.py::TestNotFound::test_unknown_path_returns_404 PASSED
tests/test_main.py::TestNotFound::test_unknown_path_error_format PASSED

========================= 9 passed in x.xxs =========================
```

✅ **확인**: Java 4개 + Node.js 4개 + Python 9개 = **총 17개 테스트 전체 통과**

---

### Step 6: APP_VERSION 환경변수 동작 확인

CI 파이프라인에서 git SHA를 버전으로 주입하는 메커니즘을 미리 확인합니다.

```bash
# python-app에서 환경변수 버전 주입 테스트
cd ~/workspace/git-argocd-lecture/app/python-app
source .venv/bin/activate

# APP_VERSION 환경변수를 지정해서 실행
APP_VERSION="test-sha-abc1234" uvicorn app.main:app --host 0.0.0.0 --port 8001 &

# 잠시 대기 후 확인
sleep 2
curl -s http://localhost:8001/ | python3 -m json.tool
curl -s http://localhost:8001/health | python3 -m json.tool

# 백그라운드 프로세스 종료
kill %1
```

```
예상 출력:
{
    "app": "python-app",
    "version": "test-sha-abc1234",   ← 환경변수 반영됨
    "language": "Python",
    ...
}

{
    "status": "ok",
    "app": "python-app",
    "version": "test-sha-abc1234"   ← 헬스체크에도 반영
}
```

✅ **확인**: `APP_VERSION` 환경변수 값이 API 응답의 `"version"` 필드에 반영

---

## ✅ 확인 체크리스트

- [ ] 프로젝트 디렉토리 구조 파악 (`app/`, `.github/`, `manifests/`, `argocd/`)
- [ ] `java-app` — `GET /` 응답에 `"app": "java-app"` 포함 확인
- [ ] `java-app` — `GET /health` 응답에 `"status": "ok"` 확인
- [ ] `node-app` — `GET /`, `GET /health` 응답 확인 (앱명: `node-app`)
- [ ] `python-app` — `GET /`, `GET /health` 응답 확인 (앱명: `python-app`)
- [ ] java-app 단위 테스트 4개 전체 통과 (`BUILD SUCCESS`)
- [ ] node-app 단위 테스트 4개 전체 통과 (`pass 4, fail 0`)
- [ ] python-app 단위 테스트 9개 전체 통과 (`9 passed`)
- [ ] `APP_VERSION` 환경변수가 API 응답 `"version"` 필드에 반영되는 것 확인

---

**다음 단계**: [03. Dockerfile 작성 + 이미지 빌드 + 레지스트리 푸시](03-dockerize.md)
