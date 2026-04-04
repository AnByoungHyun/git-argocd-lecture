# 03. Dockerfile 작성 + 이미지 빌드 + 레지스트리 푸시

> 가이드 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 상태: ✅ 실습 단계 작성 완료  
> 이전 가이드: [02. 샘플 앱 구조 이해](02-sample-apps.md) | 다음 가이드: [04. GitHub Actions CI](04-github-actions.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] 멀티스테이지 Dockerfile의 구조와 장점을 설명할 수 있다
- [ ] 3개 앱의 Dockerfile을 분석하고 각 Stage의 역할을 이해한다
- [ ] 로컬에서 이미지를 빌드하고 컨테이너를 실행해 동작을 확인할 수 있다
- [ ] GHCR에 이미지를 로그인하고 푸시할 수 있다

---

## 📖 이론

### 🖥️ 컨테이너 vs 가상 머신(VM)

컨테이너를 처음 접하면 "VM이랑 뭐가 다르지?"라는 질문이 자연스럽게 나옵니다.

```
┌──────────────────────────────┐    ┌──────────────────────────────┐
│        가상 머신 (VM)          │    │       컨테이너 (Docker)        │
├──────────────────────────────┤    ├──────────────────────────────┤
│  App A  │  App B  │  App C   │    │  App A  │  App B  │  App C   │
├─────────┼─────────┼──────────┤    ├─────────┴─────────┴──────────┤
│Guest OS │Guest OS │ Guest OS  │    │      Container Runtime        │
├─────────┴─────────┴──────────┤    ├──────────────────────────────┤
│        Hypervisor             │    │         Host OS (Kernel)      │
├──────────────────────────────┤    ├──────────────────────────────┤
│          Hardware             │    │          Hardware             │
└──────────────────────────────┘    └──────────────────────────────┘
  각 VM마다 전체 OS 포함 → GB 단위     OS 커널 공유 → MB 단위, 초 단위 기동
```

| 항목 | 가상 머신 (VM) | 컨테이너 |
|------|--------------|---------|
| **격리 수준** | 하드웨어 수준 (강함) | 프로세스 수준 |
| **이미지 크기** | GB 단위 (OS 포함) | MB 단위 (앱+의존성만) |
| **기동 시간** | 분 단위 | 초 단위 |
| **리소스 효율** | 낮음 (OS 오버헤드) | 높음 (커널 공유) |
| **이식성** | OS 의존 | 어디서나 동일하게 실행 |
| **K8s 적합성** | 어려움 | 최적 (Pod = 컨테이너 그룹) |

> 💡 **컨테이너가 CI/CD에 적합한 이유**: 빌드 환경과 실행 환경이 완전히 동일합니다. "내 PC에서는 됐는데 서버에서 안 돼요" 문제가 사라집니다.

---

### 📋 Dockerfile 주요 명령어 참조

Dockerfile은 이미지를 만드는 **레시피**입니다. 주요 명령어를 알면 Dockerfile을 읽고 수정할 수 있습니다.

| 명령어 | 역할 | 예시 |
|--------|------|------|
| `FROM` | 베이스 이미지 지정 (모든 Dockerfile의 시작) | `FROM eclipse-temurin:17-jre-alpine` |
| `WORKDIR` | 작업 디렉토리 설정 (없으면 자동 생성) | `WORKDIR /app` |
| `COPY` | 호스트 파일을 이미지로 복사 | `COPY src ./src` |
| `RUN` | 이미지 빌드 중 명령 실행 (레이어 생성) | `RUN mvn package -DskipTests` |
| `EXPOSE` | 컨테이너가 사용할 포트 문서화 (실제 포트 개방은 `docker run -p`로) | `EXPOSE 8080` |
| `CMD` | 컨테이너 시작 시 기본 실행 명령 (오버라이드 가능) | `CMD ["node", "src/index.js"]` |
| `ENTRYPOINT` | 컨테이너 진입점 (CMD와 결합 사용, 오버라이드 어려움) | `ENTRYPOINT ["java", "-jar", "app.jar"]` |
| `ARG` | 빌드 시점에만 사용 가능한 변수 (`--build-arg`로 주입) | `ARG APP_VERSION=1.0.0` |
| `ENV` | 런타임 환경변수 설정 (컨테이너 실행 중에도 유지) | `ENV APP_VERSION=${APP_VERSION}` |
| `USER` | 이후 명령 실행 사용자 변경 (보안: non-root) | `USER appuser` |
| `HEALTHCHECK` | 컨테이너 헬스체크 명령 정의 | `HEALTHCHECK CMD wget -qO- http://localhost:8080/health` |

#### ARG vs ENV 차이점

```dockerfile
ARG APP_VERSION=1.0.0        # 빌드 시점만 존재 → 실행 중인 컨테이너에서 echo $APP_VERSION 하면 빈값
ENV APP_VERSION=${APP_VERSION} # 런타임 환경변수 → 실행 중에도 유지, 앱 코드에서 읽기 가능

# CI에서 빌드할 때:
# docker build --build-arg APP_VERSION=abc1234 .
#                                    ↑ ARG로 받아서 ENV로 전달 → 앱이 os.getenv("APP_VERSION") 으로 읽음
```

#### CMD vs ENTRYPOINT 차이점

```dockerfile
# CMD: docker run 시 명령을 덮어쓸 수 있음
CMD ["node", "src/index.js"]
# docker run myapp /bin/sh  ← CMD를 /bin/sh로 덮어씀 (디버깅 시 활용)

# ENTRYPOINT: 항상 실행됨, CMD는 인자로 붙음 (Java 앱에 JVM 옵션 줄 때 유용)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
```

---

### 컨테이너 이미지와 Dockerfile

**Docker 이미지**는 애플리케이션 실행에 필요한 모든 것(코드, 런타임, 의존성, 설정)을  
레이어 구조로 패키징한 읽기 전용 템플릿입니다.

```
이미지 레이어 구조 예시 (java-app):
┌─────────────────────────────┐
│   COPY application/ (앱)    │  ← 가장 자주 변경
├─────────────────────────────┤
│   COPY snapshot-deps/       │
├─────────────────────────────┤
│   COPY spring-boot-loader/  │
├─────────────────────────────┤
│   COPY dependencies/ (lib)  │  ← 거의 변경 안 됨
├─────────────────────────────┤
│   eclipse-temurin:17-jre    │  ← 베이스 이미지
└─────────────────────────────┘
변경된 레이어 이상만 다시 빌드 → 캐시 효율 극대화
```

### 멀티스테이지 빌드

**문제**: 빌드 도구(Maven, npm)를 런타임 이미지에 포함하면 이미지가 커지고 보안 위험이 증가합니다.

**해결**: 여러 `FROM` 명령으로 스테이지를 분리하고, 최종 런타임 스테이지에는 필요한 파일만 복사합니다.

```dockerfile
# 스테이지 1: 빌드 (Maven + JDK → 빌드 결과물만 생성)
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
...

# 스테이지 2: 레이어 추출 (Spring Boot layertools)
FROM eclipse-temurin:17-jre-alpine AS layers
...

# 스테이지 3: 런타임 (JRE만 포함 — 빌드 도구 없음)
FROM eclipse-temurin:17-jre-alpine AS runtime
COPY --from=layers /app/...   ← 이전 스테이지에서 필요한 파일만 복사
```

**효과 비교**:

| 방식 | 이미지 크기 | 보안 |
|------|-----------|------|
| 단일 스테이지 (JDK + Maven 포함) | ~700MB | 빌드 도구 노출 위험 |
| 멀티스테이지 (JRE만 런타임) | ~200MB | 빌드 도구 미포함 ✅ |

### java-app Dockerfile 분석

```dockerfile
# ── Stage 1: builder ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q      # ① pom.xml만 먼저 복사 → 의존성 캐시 레이어 분리
COPY src ./src
RUN mvn package -DskipTests -q        # ② 소스 빌드 (테스트 스킵 — CI에서 별도 실행)

# ── Stage 2: layers ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS layers
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract  # ③ Spring Boot 레이어 분리

# ── Stage 3: runtime ──────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup  # ④ non-root 사용자
WORKDIR /app
COPY --from=layers /app/dependencies/ ./        # ⑤ 레이어 순서대로 복사 (캐시 최적화)
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./
RUN chown -R appuser:appgroup /app
USER appuser
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION}                   # ⑥ 빌드 시 버전 주입
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", ..., "org.springframework.boot.loader.launch.JarLauncher"]
HEALTHCHECK --interval=30s ...                   # ⑦ 컨테이너 헬스체크
```

**설계 포인트**:
- **①** `pom.xml` 먼저 복사 → 소스 변경 시 의존성 레이어 캐시 재사용
- **④** non-root 사용자(`appuser`) — 컨테이너 탈출 시 권한 최소화
- **⑥** `ARG/ENV`로 git SHA 주입 → CI에서 `--build-arg APP_VERSION=$GITHUB_SHA` 전달
- **⑦** HEALTHCHECK → `docker run` 시 자동 헬스체크, K8s probe와 별개

### node-app Dockerfile 분석

```dockerfile
# ── Stage 1: deps ─────────────────────────────────────────────
FROM node:18-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev --ignore-scripts  # ① package-lock.json 기반 재현 설치 (프로덕션만)

# ── Stage 2: runtime ──────────────────────────────────────────
FROM node:18-alpine AS runtime
RUN apk add --no-cache wget             # ② HEALTHCHECK용 wget
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY src ./src
COPY package.json ./
RUN chown -R node:node /app
USER node                                # ③ 기본 제공 node 사용자 (UID 1000)
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION} PORT=3000 NODE_ENV=production
EXPOSE 3000
CMD ["node", "src/index.js"]
HEALTHCHECK --interval=30s ...
```

**설계 포인트**:
- **①** `npm ci` — `package-lock.json` 필수 (`npm install`과 다르게 lock 파일 기반으로 재현 설치)
- **③** alpine 이미지에 내장된 `node` 사용자 사용 (별도 생성 불필요)

### python-app Dockerfile 분석

```dockerfile
# ── Stage 1: deps ─────────────────────────────────────────────
FROM python:3.11-slim AS deps
WORKDIR /install
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install/packages \
      -r requirements.txt              # ① 의존성을 /install/packages에 격리 설치

# ── Stage 2: runtime ──────────────────────────────────────────
FROM python:3.11-slim AS runtime
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
RUN apt-get update -q && apt-get install -y wget && rm -rf /var/lib/apt/lists/*  # ② HEALTHCHECK용
WORKDIR /app
COPY --from=deps /install/packages /usr/local  # ③ 의존성 복사
COPY app ./app
RUN chown -R appuser:appgroup /app
USER appuser
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION} PORT=8000
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-access-log"]
HEALTHCHECK --interval=30s ...
```

### 🔐 non-root 사용자 실행 — 왜 보안에 중요한가?

컨테이너 기본 설정은 `root` 사용자로 실행됩니다. 이것이 왜 문제인지 이해해봅시다.

#### root 실행의 위험

```
컨테이너 내부 프로세스 (root로 실행 중)
    │
    │ 애플리케이션 취약점으로 공격자가 쉘 획득
    ▼
/etc/passwd 읽기  ✅ 가능
민감한 파일 삭제  ✅ 가능
호스트 파일시스템 마운트 접근  ⚠️ 설정에 따라 가능
컨테이너 탈출 시 호스트 root 권한  ⚠️ 위험
```

#### non-root 설정 방법 (각 앱별 비교)

```dockerfile
# Java (alpine 기반) — 사용자 직접 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Node.js (node:18-alpine) — 이미지에 내장된 'node' 사용자 활용
USER node              # UID 1000, 별도 생성 불필요

# Python (python:3.11-slim) — 사용자 직접 생성
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser
```

#### non-root 실행 확인

```bash
# 컨테이너 내부에서 실행 중인 사용자 확인
docker exec java-app-test whoami
# 예상 출력: appuser  (root가 아님!)

docker exec node-app-test whoami
# 예상 출력: node
```

> 💡 **K8s 보안 정책**: `PodSecurityPolicy` 또는 `SecurityContext`의 `runAsNonRoot: true`로 root 실행을 클러스터 수준에서 강제할 수 있습니다.

| 설정 | 설명 |
|------|------|
| `runAsNonRoot: true` | root UID(0)로 실행 시 Pod 시작 거부 |
| `readOnlyRootFilesystem: true` | 파일시스템 읽기 전용 (쓰기 불가) |
| `allowPrivilegeEscalation: false` | 권한 상승 차단 |

---

### 🏷️ 이미지 태그 전략 — latest vs git SHA

이미지 태그는 단순한 버전 표시가 아니라 **GitOps의 핵심 메커니즘**입니다.

#### latest 태그의 문제점

```
# 배포 히스토리 예시
docker push myapp:latest  ← 월요일 배포 (기능 A 포함)
docker push myapp:latest  ← 수요일 배포 (기능 B 포함)
docker push myapp:latest  ← 금요일 배포 (버그 포함!)

문제 1: 금요일 배포가 버그인 걸 발견 → 어떤 버전으로 롤백?
문제 2: K8s에서 imagePullPolicy: IfNotPresent 면 새 이미지를 가져오지 않을 수 있음
문제 3: GitOps에서 Git의 상태와 실제 배포 상태를 추적 불가
```

#### git SHA 태그의 장점

```
# CI 파이프라인 흐름
git commit abc1234  (기능 A)
    │
    ▼
docker build → push ghcr.io/org/java-app:abc1234
    │
    ▼
manifests/java-app/deployment.yaml 업데이트
  image: ghcr.io/org/java-app:abc1234   ← 정확히 이 커밋의 이미지
    │
    ▼
ArgoCD가 Git 변경 감지 → K8s에 배포
```

```
# 버그 발생 시 롤백
git revert → 커밋 def5678 생성
    │
    ▼
docker push ghcr.io/org/java-app:def5678
    │
    ▼
deployment.yaml: image: ...java-app:def5678
    │
    ▼
ArgoCD 자동 동기화 → 안전하게 이전 상태로 복원
```

#### 이 프로젝트의 태그 전략

| 태그 | 생성 시점 | 용도 |
|------|---------|------|
| `abc1234` (7자리 SHA) | 모든 push/PR | 정확한 버전 추적, K8s 배포에 사용 |
| `latest` | `main` 브랜치 push 시만 | 로컬 테스트 편의용 (K8s 배포에 사용 금지) |

```bash
# CI에서 실제 태그 생성 방식 (GitHub Actions)
GIT_SHA=$(git rev-parse --short HEAD)    # abc1234
docker build -t myapp:$GIT_SHA .
docker push myapp:$GIT_SHA
docker tag myapp:$GIT_SHA myapp:latest   # main 브랜치에서만
docker push myapp:latest
```

> 💡 **GitOps 원칙**: `latest`로 배포하면 Git 저장소의 상태와 실제 클러스터 상태가 달라집니다.  
> ArgoCD는 이를 "OutOfSync"로 판단하지 못하므로, **항상 불변 태그(SHA)를 사용**해야 합니다.

---

### GitHub Container Registry (GHCR)

**GHCR**은 GitHub에 내장된 컨테이너 이미지 레지스트리입니다.

| 항목 | 값 |
|------|-----|
| 레지스트리 주소 | `ghcr.io` |
| 이미지 이름 규칙 | `ghcr.io/<github-username>/<image-name>:<tag>` |
| 인증 | GitHub PAT (`write:packages` 권한) |
| 예시 | `ghcr.io/anbyeonghyun/java-app:abc1234` |

---

## 🛠️ 실습 단계

> ⚠️ **사전 조건**: Rancher Desktop 실행 중, `docker version` 동작 확인, GitHub PAT의 `GITHUB_TOKEN` 환경변수 설정

---

### Step 1: 환경변수 준비

```bash
# 아직 설정하지 않은 경우
export GITHUB_USERNAME="your-github-username"   # 본인 GitHub 사용자명으로 변경
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxx"  # 본인 PAT로 변경

# 확인
echo "Username: $GITHUB_USERNAME"
echo "Token: ${GITHUB_TOKEN:0:10}..."           # 앞 10자리만 표시 (보안)
```

```
예상 출력:
Username: your-github-username
Token: ghp_xxxxxx...
```

✅ **확인**: 두 환경변수가 올바르게 설정되면 성공

---

### Step 2: java-app 이미지 빌드

Spring Boot 앱의 3단계 멀티스테이지 빌드를 실행합니다.  
**처음 빌드 시 Maven 의존성 다운로드로 3~5분 소요됩니다.**

```bash
cd ~/workspace/git-argocd-lecture/app/java-app

# 이미지 빌드 (로컬 테스트용 태그)
docker build \
  --build-arg APP_VERSION=local-test \
  -t ghcr.io/$GITHUB_USERNAME/java-app:local \
  .
```

```
예상 출력:
[+] Building xx.xs (16/16) FINISHED
 => [internal] load build definition from Dockerfile               0.0s
 => [builder 1/4] FROM maven:3.9-eclipse-temurin-17-alpine@sha... 0.0s
 => [builder 2/4] WORKDIR /build                                   0.0s
 => [builder 3/4] COPY pom.xml .                                   0.0s
 => [builder 4/4] RUN mvn dependency:go-offline -q                30.0s  ← 첫 실행 시 오래 걸림
 => [layers 1/2] COPY --from=builder /build/target/*.jar app.jar   0.5s
 => [layers 2/2] RUN java -Djarmode=layertools -jar app.jar...     1.0s
 => [runtime 1/7] RUN addgroup -S appgroup && adduser -S appuser   0.5s
 => [runtime 5/8] COPY --from=layers /app/application/ ./         0.1s
 => exporting to image                                             0.5s
 => => writing image sha256:xxxx...                                0.0s
 => => naming to ghcr.io/username/java-app:local                   0.0s
```

```bash
# 빌드된 이미지 확인
docker images | grep java-app
```

```
예상 출력:
ghcr.io/username/java-app   local   sha256:xxx   x seconds ago   ~200MB
```

✅ **확인**: `Successfully built` 또는 `FINISHED` 메시지, 이미지 크기 ~200MB 이하

---

### Step 3: node-app 이미지 빌드

```bash
cd ~/workspace/git-argocd-lecture/app/node-app

docker build \
  --build-arg APP_VERSION=local-test \
  -t ghcr.io/$GITHUB_USERNAME/node-app:local \
  .
```

```
예상 출력:
[+] Building xx.xs (10/10) FINISHED
 => [deps 1/3] FROM node:18-alpine                                 0.0s
 => [deps 2/3] COPY package*.json ./                               0.1s
 => [deps 3/3] RUN npm ci --omit=dev --ignore-scripts              5.0s
 => [runtime 1/6] RUN apk add --no-cache wget                      2.0s
 => [runtime 3/6] COPY --from=deps /app/node_modules ./            0.5s
 => exporting to image                                             0.3s
 => => naming to ghcr.io/username/node-app:local
```

```bash
docker images | grep node-app
```

```
예상 출력:
ghcr.io/username/node-app   local   sha256:xxx   x seconds ago   ~180MB
```

✅ **확인**: 빌드 완료, `npm ci` 단계에서 `package-lock.json` 기반으로 설치됨

---

### Step 4: python-app 이미지 빌드

```bash
cd ~/workspace/git-argocd-lecture/app/python-app

docker build \
  --build-arg APP_VERSION=local-test \
  -t ghcr.io/$GITHUB_USERNAME/python-app:local \
  .
```

```
예상 출력:
[+] Building xx.xs (11/11) FINISHED
 => [deps 1/3] FROM python:3.11-slim                               0.0s
 => [deps 2/3] COPY requirements.txt .                             0.1s
 => [deps 3/3] RUN pip install --no-cache-dir ...                 15.0s
 => [runtime 1/6] RUN groupadd -r appgroup && useradd ...          0.5s
 => [runtime 2/6] RUN apt-get update ...                           5.0s
 => exporting to image                                             0.3s
 => => naming to ghcr.io/username/python-app:local
```

```bash
# 3개 앱 이미지 한 번에 확인
docker images | grep -E "(java|node|python)-app"
```

```
예상 출력:
ghcr.io/username/java-app    local   sha256:aaa   x seconds ago   ~200MB
ghcr.io/username/node-app    local   sha256:bbb   x seconds ago   ~180MB
ghcr.io/username/python-app  local   sha256:ccc   x seconds ago   ~160MB
```

✅ **확인**: 3개 이미지 모두 존재, 각각 250MB 이하

---

### Step 5: 컨테이너 실행 및 동작 확인

빌드한 이미지를 실제로 컨테이너로 실행해 동작을 검증합니다.

**5-1. java-app 컨테이너 실행**

```bash
# 컨테이너 실행 (백그라운드, 포트 포워딩)
docker run -d \
  --name java-app-test \
  -p 8080:8080 \
  -e APP_VERSION=container-test \
  ghcr.io/$GITHUB_USERNAME/java-app:local

# 컨테이너 시작 대기 (Spring Boot 초기화 약 10~15초)
sleep 15

# 헬스체크 확인
curl -s http://localhost:8080/health | python3 -m json.tool

# 기본 정보 확인
curl -s http://localhost:8080/ | python3 -m json.tool
```

```
예상 출력 (/health):
{
    "status": "ok",
    "app": "java-app",
    "version": "container-test"    ← --build-arg 또는 -e 환경변수 반영
}

예상 출력 (/):
{
    "app": "java-app",
    "version": "container-test",
    "language": "Java",
    "framework": "Spring Boot 3.x",
    "port": 8080,
    "environment": "production"
}
```

```bash
# Docker 헬스체크 상태 확인 (healthy 되기까지 약 60초)
docker inspect --format='{{.State.Health.Status}}' java-app-test
```

```
예상 출력:
healthy
```

**5-2. node-app 컨테이너 실행**

```bash
docker run -d \
  --name node-app-test \
  -p 3000:3000 \
  -e APP_VERSION=container-test \
  ghcr.io/$GITHUB_USERNAME/node-app:local

sleep 3

curl -s http://localhost:3000/health | python3 -m json.tool
```

```
예상 출력:
{
    "status": "ok",
    "app": "node-app",
    "version": "container-test"
}
```

**5-3. python-app 컨테이너 실행**

```bash
docker run -d \
  --name python-app-test \
  -p 8000:8000 \
  -e APP_VERSION=container-test \
  ghcr.io/$GITHUB_USERNAME/python-app:local

sleep 3

curl -s http://localhost:8000/health | python3 -m json.tool
```

```
예상 출력:
{
    "status": "ok",
    "app": "python-app",
    "version": "container-test"
}
```

**5-4. 실행 중인 컨테이너 목록 확인**

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

```
예상 출력:
NAMES             STATUS                    PORTS
python-app-test   Up x seconds (healthy)    0.0.0.0:8000->8000/tcp
node-app-test     Up x seconds (healthy)    0.0.0.0:3000->3000/tcp
java-app-test     Up x minutes (healthy)    0.0.0.0:8080->8080/tcp
```

**5-5. 테스트 후 컨테이너 정리**

```bash
docker rm -f java-app-test node-app-test python-app-test
```

✅ **확인**: 3개 컨테이너 모두 `(healthy)` 상태, `/health` 응답에 `"status": "ok"` 확인

---

### Step 6: APP_VERSION 환경변수 주입 확인

CI 파이프라인에서 git SHA를 이미지 빌드 시 주입하는 방식을 시뮬레이션합니다.

```bash
# 현재 git SHA 확인 (CI에서 GITHUB_SHA 환경변수로 제공)
GIT_SHA=$(git -C ~/workspace/git-argocd-lecture rev-parse --short HEAD)
echo "Current SHA: $GIT_SHA"

# SHA를 APP_VERSION으로 주입해 빌드
docker build \
  --build-arg APP_VERSION=$GIT_SHA \
  -t ghcr.io/$GITHUB_USERNAME/java-app:$GIT_SHA \
  ~/workspace/git-argocd-lecture/app/java-app/

# 실행 후 버전 확인
docker run -d --name java-sha-test -p 8080:8080 \
  ghcr.io/$GITHUB_USERNAME/java-app:$GIT_SHA

sleep 15
curl -s http://localhost:8080/ | python3 -m json.tool

# 정리
docker rm -f java-sha-test
```

```
예상 출력:
{
    "app": "java-app",
    "version": "abc1234",        ← 실제 git SHA가 version에 반영
    "language": "Java",
    ...
}
```

✅ **확인**: `"version"` 필드에 git SHA가 반영되면 CI 버전 주입 메커니즘 검증 완료

---

### Step 7: GHCR 로그인 및 이미지 푸시 (선택)

GHCR에 이미지를 푸시해 CI 파이프라인에서 사용할 이미지를 등록합니다.

> 💡 **선택 단계**: 이 단계는 GitHub Actions CI(가이드 04)에서 자동으로 수행됩니다.  
> 직접 푸시 메커니즘을 이해하고 싶은 경우에만 진행하세요.

**7-1. GHCR 로그인**

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin
```

```
예상 출력:
WARNING! Your password will be stored unencrypted in /Users/xxx/.docker/config.json.
Configure a credential helper to remove this warning. See
https://docs.docker.com/engine/reference/commandline/login/#credentials-store

Login Succeeded
```

**7-2. 이미지 푸시**

```bash
# java-app 푸시
docker push ghcr.io/$GITHUB_USERNAME/java-app:local

# node-app 푸시
docker push ghcr.io/$GITHUB_USERNAME/node-app:local

# python-app 푸시
docker push ghcr.io/$GITHUB_USERNAME/python-app:local
```

```
예상 출력 (java-app):
The push refers to repository [ghcr.io/username/java-app]
xxx: Pushed
xxx: Pushed
...
local: digest: sha256:xxx size: xxxx
```

**7-3. GHCR에서 이미지 확인**

```bash
# 브라우저에서 확인
open https://github.com/$GITHUB_USERNAME?tab=packages
```

```
예상 화면:
Packages
  java-app    Published x seconds ago
  node-app    Published x seconds ago
  python-app  Published x seconds ago
```

**7-4. 이미지 가시성 설정 (공개)**

> 새로 생성된 GHCR 패키지는 기본적으로 **Private**입니다.  
> K8s에서 `imagePullSecrets` 없이 사용하려면 Public으로 변경이 필요합니다.

1. `https://github.com/$GITHUB_USERNAME?tab=packages` 접속
2. 각 패키지 클릭 → **Package settings** → **Change visibility** → **Public**
3. 또는 K8s Secret(`ghcr-secret`)을 생성해 Private 이미지를 사용 (가이드 06 참조)

✅ **확인**: GitHub → Packages에서 3개 이미지가 표시되면 성공

---

### Step 8: 로컬 이미지 정리 (선택)

실습 후 디스크 공간 확보를 위해 로컬 이미지를 정리합니다.

```bash
# 특정 이미지만 삭제
docker rmi \
  ghcr.io/$GITHUB_USERNAME/java-app:local \
  ghcr.io/$GITHUB_USERNAME/node-app:local \
  ghcr.io/$GITHUB_USERNAME/python-app:local

# 또는 사용하지 않는 이미지 전체 정리
docker image prune -f

# 현재 남은 이미지 확인
docker images
```

---

## ✅ 확인 체크리스트

- [ ] `docker images` — `java-app:local`, `node-app:local`, `python-app:local` 3개 이미지 빌드 성공
- [ ] java-app 컨테이너 실행 후 `GET /health` — `"status": "ok"` 확인
- [ ] node-app 컨테이너 실행 후 `GET /health` — `"status": "ok"` 확인
- [ ] python-app 컨테이너 실행 후 `GET /health` — `"status": "ok"` 확인
- [ ] `--build-arg APP_VERSION=<sha>` 가 API 응답 `"version"` 필드에 반영 확인
- [ ] (선택) `docker login ghcr.io` — `Login Succeeded` 확인
- [ ] (선택) GHCR 3개 이미지 푸시 완료 — GitHub → Packages에서 확인

---

**다음 단계**: [04. GitHub Actions CI 파이프라인](04-github-actions.md)
