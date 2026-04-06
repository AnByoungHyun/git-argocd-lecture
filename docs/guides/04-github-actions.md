# 04. GitHub Actions CI 파이프라인 구성

> 가이드 버전: 1.1.0  
> 최종 수정: 2026-04-06  
> 상태: ✅ 완료 (3-Job 체인, update-manifest 추가)  
> 이전 가이드: [03. Dockerfile 작성](03-dockerize.md) | 다음 가이드: [05. K8s 매니페스트](05-k8s-manifests.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] GitHub Actions 워크플로우 YAML 구조(on/jobs/steps)를 이해하고 설명할 수 있다
- [ ] 트리거 조건(push/pull_request, path 필터)과 Job 의존성(`needs`)을 설명할 수 있다
- [ ] main 브랜치 push 후 CI가 자동 실행되어 이미지가 GHCR에 푸시되는 것을 확인할 수 있다
- [ ] PR 생성 시 빌드/테스트만 실행되고 이미지 푸시가 없음을 확인할 수 있다

---

## 📖 이론

> 💡 **GitHub Actions를 처음 접한다면 여기서부터 시작하세요.**  
> 실습 전에 이 이론 섹션을 읽으면 각 YAML 키워드가 왜 그렇게 작성되었는지 이해할 수 있습니다.

---

### 1. GitHub Actions 아키텍처

#### 1-1. Workflow → Job → Step 계층 구조

GitHub Actions는 **4계층 구조**로 구성됩니다. 위에서 아래로 포함 관계입니다.

```
Workflow  (.github/workflows/ci-java.yml)
└── on: push / pull_request          ← 이벤트 트리거
    └── Job: build-and-test          ← 독립 실행 단위 (가상 머신 1대)
        ├── Step 1: Checkout         ← 순차 실행 명령
        ├── Step 2: Set up Java 17
        └── Step 3: Run tests
    └── Job: docker-build-push       ← 별도 가상 머신에서 실행
        ├── Step 1: Checkout
        ├── Step 2: Docker Login
        └── Step 3: Build & Push
    └── Job: update-manifest         ← main push시에만 실행
        ├── Step 1: Checkout
        ├── Step 2: image 태그 업데이트 (awk)
        └── Step 3: git commit + push
```

| 계층 | 역할 | 파일/키워드 |
|------|------|-----------|
| **Workflow** | 하나의 자동화 시나리오 정의 | `.github/workflows/*.yml` |
| **Job** | 독립된 실행 환경(Runner) 1개에서 동작 | `jobs:` 하위 각 키 |
| **Step** | Job 안에서 순서대로 실행되는 단위 작업 | `steps:` 하위 각 항목 |
| **Action** | 재사용 가능한 Step 패키지 (누군가 만들어 둔 것) | `uses: actions/checkout@v4` |

> ⚠️ **중요**: Job끼리는 **병렬 실행**이 기본입니다.  
> 순서를 강제하려면 `needs: <이전 Job명>`을 명시해야 합니다.  
> 이 프로젝트에서 `docker-build-push`가 `needs: build-and-test`를,  
> `update-manifest`가 `needs: docker-build-push`를 사용하는 이유입니다.

---

#### 1-2. Runner — 코드를 실행하는 가상 머신

**Runner**는 Job을 실제로 실행하는 서버(가상 머신)입니다.  
`runs-on: ubuntu-latest`로 지정하면 GitHub가 Ubuntu 22.04 머신을 즉시 제공합니다.

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest   # ← GitHub이 관리하는 Ubuntu VM에서 실행
```

| Runner 종류 | 설명 | 비용 |
|------------|------|------|
| `ubuntu-latest` | Ubuntu 22.04 (가장 많이 사용) | GitHub 무료 제공 |
| `windows-latest` | Windows Server 2022 | GitHub 무료 제공 |
| `macos-latest` | macOS Monterey | GitHub 무료 제공 (느림) |
| Self-hosted | 직접 관리하는 서버 | 인프라 비용 직접 부담 |

> 💡 Job이 끝나면 Runner는 삭제됩니다. 다음 실행 시 깨끗한 머신이 새로 생성됩니다.  
> 따라서 **Job 간에 파일을 공유하려면 캐시나 Artifact를 사용**해야 합니다.

---

#### 1-3. 이벤트 트리거 종류

워크플로우가 언제 실행될지 `on:` 블록으로 정의합니다.

```yaml
on:
  push:                      # ← 코드를 push할 때
    branches: [main]         #   main 브랜치에 push할 때만
    paths:                   #   특정 경로 변경 시에만 (path 필터)
      - 'app/java-app/**'
  pull_request:              # ← PR을 열거나 업데이트할 때
    branches: [main]         #   main을 대상으로 하는 PR
    paths:
      - 'app/java-app/**'
  workflow_dispatch:         # ← GitHub UI에서 수동으로 실행
```

| 이벤트 | 언제 발생 | 주요 용도 |
|--------|---------|---------|
| `push` | 브랜치에 커밋 push 시 | 빌드 + 테스트 + 이미지 푸시 |
| `pull_request` | PR 생성/업데이트 시 | 빌드 + 테스트만 (코드 검증) |
| `workflow_dispatch` | GitHub UI에서 수동 실행 | 특정 시점 수동 배포, 디버깅 |
| `schedule` | cron 표현식 기반 주기 실행 | 야간 통합 테스트 등 |

---

### 2. Path 필터와 모노레포 전략

#### 2-1. paths 필터 동작 원리

이 프로젝트는 **모노레포(monorepo)** 구조입니다. 하나의 저장소에 3개 앱이 함께 있습니다.

```
git-argocd-lecture/
├── app/java-app/      ← Java 앱
├── app/node-app/      ← Node.js 앱
├── app/python-app/    ← Python 앱
└── manifests/         ← K8s 매니페스트
```

`paths` 필터가 없으면 어느 파일을 수정해도 3개 CI가 모두 실행됩니다.  
`paths` 필터를 적용하면 **변경된 앱의 CI만 실행**됩니다.

```yaml
# ci-java.yml — java-app 변경 시에만 실행
on:
  push:
    paths:
      - 'app/java-app/**'   # ← app/java-app/ 하위 파일 변경 시에만 트리거

# ci-node.yml — node-app 변경 시에만 실행
on:
  push:
    paths:
      - 'app/node-app/**'
```

```
  ┌──────────────────────────────────┐
  │            git push              │
  │  app/node-app/index.js 수정      │
  └──────────────────────────────────┘
         │             │            │
         ▼             ▼            ▼
  ┌────────────┐ ┌────────────┐ ┌────────────┐
  │ci-java.yml │ │ci-node.yml │ │ci-python   │
  │❌ 미트리거 │ │✅ 트리거됨 │ │   .yml     │
  │java 변경X  │ │node 변경   │ │❌ 미트리거 │
  └────────────┘ └────────────┘ └────────────┘
```

#### 2-2. 모노레포에서 앱별 독립 파이프라인의 장점

| 항목 | 독립 파이프라인 적용 시 | 미적용 시 |
|------|----------------------|---------|
| **실행 시간** | 변경된 앱만 CI 실행 → 빠름 | 모든 앱 CI 실행 → 느림 |
| **비용** | GitHub Actions 실행 시간 절약 | 불필요한 실행 비용 발생 |
| **실패 격리** | Java 테스트 실패가 Node CI에 영향 없음 | 하나 실패가 전체에 영향 가능 |
| **이미지 오염 방지** | 변경된 앱만 새 이미지 생성 | 변경 없는 앱도 이미지 재빌드 |

---

### 3. GitHub Actions의 인증과 권한

#### 3-1. GITHUB_TOKEN 자동 발급 메커니즘

GitHub Actions가 실행될 때마다 **GITHUB_TOKEN**이 자동으로 발급됩니다.  
별도로 토큰을 만들거나 Secrets에 등록할 필요가 없습니다.

```
워크플로우 실행 시작
        │
        ▼
GitHub가 임시 GITHUB_TOKEN 자동 발급
(해당 워크플로우 실행 동안만 유효)
        │
        ▼
secrets.GITHUB_TOKEN 으로 접근 가능
        │
        ▼
워크플로우 종료 시 토큰 자동 만료(폐기)
```

```yaml
# 사용 예시 — 별도 설정 없이 바로 사용 가능
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}   # ← 자동 발급, 별도 등록 불필요
```

---

#### 3-2. permissions 블록의 역할

`GITHUB_TOKEN`이 자동 발급되더라도, 기본적으로 **읽기 권한만** 부여됩니다.  
GHCR에 이미지를 push하려면 쓰기 권한이 필요하므로 `permissions`로 명시합니다.

```yaml
permissions:
  contents: read     # 저장소 코드 읽기 (checkout에 필요)
  packages: write    # GHCR 패키지(이미지) 쓰기 (이미지 push에 필요)
```

| 권한 | 설명 | 이 프로젝트에서 필요한 이유 |
|------|------|--------------------------|
| `contents: read` | 저장소 파일 읽기 | `actions/checkout@v4` 실행 |
| `packages: write` | GitHub Packages(GHCR) 쓰기 | `docker push` → GHCR 이미지 등록 |
| `contents: write` | 저장소 파일 쓰기 | GitOps 이미지 태그 업데이트 시 필요 (현재 미사용) |

> 💡 **최소 권한 원칙**: 필요한 권한만 명시하는 것이 보안상 올바른 방법입니다.

---

#### 3-3. PAT vs GITHUB_TOKEN 비교

| 항목 | `GITHUB_TOKEN` | PAT (Personal Access Token) |
|------|---------------|----------------------------|
| **발급 방식** | GitHub가 자동 발급 | 사람이 직접 생성 |
| **유효 기간** | 워크플로우 실행 중만 유효 | 수동 설정 (최대 1년) |
| **범위** | 현재 저장소만 접근 가능 | 계정 전체 또는 지정 범위 |
| **보안** | ✅ 높음 (자동 만료) | ⚠️ 낮음 (유출 시 장기간 위험) |
| **설정 난이도** | ✅ 별도 설정 불필요 | Settings → Developer settings에서 생성 후 Secrets 등록 |
| **권장 사용처** | 같은 저장소 작업 (GHCR 포함) | 다른 저장소 접근, 외부 서비스 인증 |

> 💡 **결론**: 이 프로젝트처럼 GHCR(같은 GitHub 계정)에 이미지를 push하는 경우  
> PAT 없이 `GITHUB_TOKEN`만으로 충분합니다.

---

### 4. Docker Build & Push Action

#### 4-1. docker/build-push-action@v5 동작 원리

`docker/build-push-action@v5`는 Docker 이미지 빌드와 레지스트리 push를 하나의 Action으로 처리합니다.

```yaml
- name: Build and push Docker image
  uses: docker/build-push-action@v5
  with:
    context: app/java-app          # Dockerfile이 있는 디렉토리
    file: app/java-app/Dockerfile  # Dockerfile 경로 (context 기준)
    push: true                     # true = 레지스트리에 push, false = 빌드만
    tags: |
      ghcr.io/myorg/java-app:abc1234   # SHA 태그
      ghcr.io/myorg/java-app:latest    # latest 태그
    cache-from: type=gha           # 캐시 불러오기
    cache-to: type=gha,mode=max    # 캐시 저장
```

```
내부 동작 흐름:
1. context 디렉토리를 Docker build context로 설정
2. Dockerfile 파싱 → 각 RUN 명령을 레이어로 빌드
3. cache-from에서 이전 레이어 캐시 확인 → 변경 없으면 재사용
4. push: true이면 tags에 명시된 레지스트리로 push
5. push: false이면 로컬 빌드만 (레지스트리 변경 없음)
```

---

#### 4-2. cache-from / cache-to — Docker 레이어 캐시

Docker 이미지는 명령어 단위로 **레이어(layer)**를 쌓습니다.  
이전 빌드와 동일한 레이어는 재사용하면 빌드 시간이 크게 단축됩니다.

```
첫 번째 빌드:
  Layer 1: FROM eclipse-temurin:17-jre    →  다운로드 (30초)
  Layer 2: COPY target/*.jar app.jar      →  복사 (5초)
  Layer 3: ENTRYPOINT [...]               →  설정 (1초)
  합계: 약 36초

두 번째 빌드 (코드만 수정):
  Layer 1: FROM eclipse-temurin:17-jre    →  ✅ 캐시 재사용 (0초)
  Layer 2: COPY target/*.jar app.jar      →  JAR 변경됨 → 재빌드 (5초)
  Layer 3: ENTRYPOINT [...]               →  재빌드 (1초)
  합계: 약 6초  ← 캐시 덕분에 83% 단축!
```

```yaml
cache-from: type=gha           # GitHub Actions Cache에서 이전 레이어 불러오기
cache-to: type=gha,mode=max    # 모든 레이어를 GitHub Actions Cache에 저장
                               # mode=max: 중간 레이어까지 모두 저장 (최대 효과)
```

> 💡 `type=gha`는 GitHub Actions 전용 캐시입니다.  
> 같은 브랜치의 이전 실행 캐시를 자동으로 재사용합니다.

---

#### 4-3. push 조건 분기: main push vs PR

```yaml
push: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main' }}
#      ↑ 이 표현식이 true일 때만 이미지를 레지스트리에 push
```

| 상황 | `github.event_name` | `github.ref` | 표현식 결과 | 동작 |
|------|--------------------|-----------|-----------|----|
| main 브랜치에 push | `push` | `refs/heads/main` | `true` | ✅ GHCR에 이미지 push |
| feature 브랜치에 push | `push` | `refs/heads/feature/...` | `false` | ❌ 빌드만 |
| PR 생성/업데이트 | `pull_request` | `refs/pull/123/merge` | `false` | ❌ 빌드만 |

```
PR 생성 시:
  Build & Test   ✅ 실행 → 코드가 올바른지 검증
  Docker Build   ✅ 실행 (push=false) → Dockerfile이 올바른지 검증
  Docker Push    ❌ 실행 안 됨 → 검증 안 된 코드의 이미지가 올라가지 않음

main push 시:
  Build & Test   ✅ 실행 → 검증
  Docker Build   ✅ 실행
  Docker Push    ✅ 실행 (push=true) → 검증된 이미지만 GHCR에 등록
```

---

### 5. CI 파이프라인 설계 원칙

#### 5-1. fail-fast: 테스트 실패 → 이미지 푸시 차단

이 프로젝트에서 CI는 **3개의 Job**으로 나뉩니다.

```yaml
jobs:
  build-and-test:           # Job 1: 테스트
    steps:
      - run: ./mvnw test    # 여기서 실패하면 이후 step 전체 중단
        working-directory: app/java-app   # ← 실행 디렉토리 지정 (중요!)

  docker-build-push:        # Job 2: 이미지 빌드 & 푸시
    needs: build-and-test   # ← Job 1이 끝나야 실행 (의존성 선언)
    if: success()           # ← Job 1이 성공했을 때만 실행

  update-manifest:          # Job 3: 매니페스트 자동 업데이트
    needs: docker-build-push  # ← Job 2 성공 후 실행
    if: github.ref == 'refs/heads/main'  # ← main push시에만
```

> ⚠️ **`working-directory` 주의사항**: GitHub Actions Runner는 저장소 루트에서 시작합니다.  
> `./mvnw` 스크립트는 `app/java-app/` 안에 있으므로 `working-directory: app/java-app`을 반드시 지정해야 합니다.  
> 지정하지 않으면 `./mvnw: No such file or directory` 오류가 발생합니다.  
> Node.js의 `setup-node` cache-dependency-path도 마찬가지로 `app/node-app/package-lock.json`으로 명시합니다.

```
  [정상 흐름 — main push]              [실패 흐름]
  ┌──────────────────────┐            ┌──────────────────────┐
  │ Job1: Build & Test   │            │ Job1: Build & Test   │
  │       ✅ 성공        │            │       ❌ 실패        │
  └──────────────────────┘            └──────────────────────┘
               │                                  │
    needs 충족 + success()              needs 미충족 → 자동 스킵
               │                                  │
               ▼                                  ▼
  ┌──────────────────────┐            ┌──────────────────────┐
  │ Job2: Docker Push    │            │ Job2: Docker Push    │
  │       ✅ 실행        │            │       ⊘ 스킵         │
  │  app:SHA GHCR 등록   │            │   GHCR 변경 없음     │
  └──────────────────────┘            └──────────────────────┘
               │
    if: github.ref == main
               │
               ▼
  ┌──────────────────────┐
  │ Job3: Update Manifest│
  │       ✅ 실행        │
  │  manifests/ SHA 갱신 │
  │  ci: update 커밋 push│
  └──────────────────────┘
```

> 💡 `needs`만 있어도 동작하지만 `if: success()`를 함께 명시하면  
> **의도가 코드에 명확히 드러나** 유지보수가 쉬워집니다.

---

#### 5-2. 이미지 오염 방지 — 실패한 코드의 이미지를 막아야 하는 이유

만약 테스트 실패와 무관하게 이미지가 GHCR에 push된다면 어떤 일이 생길까요?

```
시나리오: 버그 있는 코드가 push됨

  ❌ 나쁜 설계 (fail-fast 없음):
  1. 개발자가 버그 있는 코드 push
  2. 테스트 실패 ← 여기서 감지
  3. 그러나 이미지 빌드 & push도 실행됨
  4. ghcr.io/org/java-app:latest ← 버그 있는 이미지로 덮어씌워짐
  5. ArgoCD가 latest 이미지 자동 배포
  6. 🚨 운영 환경에 버그 있는 앱 배포됨

  ✅ 올바른 설계 (fail-fast 적용):
  1. 개발자가 버그 있는 코드 push
  2. 테스트 실패 ← 여기서 감지
  3. 이미지 빌드 & push Job 스킵됨
  4. ghcr.io/org/java-app:latest ← 이전 정상 이미지 유지
  5. ArgoCD는 변경 없음 → 정상 버전 계속 운영
  6. ✅ 운영 환경 영향 없음
```

| 원칙 | 구현 방법 | 효과 |
|------|---------|------|
| **fail-fast** | `needs` + `if: success()` | 테스트 실패 시 이후 Job 즉시 중단 |
| **이미지 오염 방지** | main push 시에만 push=true | PR/실패 코드는 레지스트리에 도달 불가 |
| **버전 추적** | git SHA 태그 사용 | 어떤 커밋의 이미지인지 항상 추적 가능 |
| **latest 안전성** | main push 성공 시에만 latest 갱신 | latest = 항상 테스트 통과한 이미지 보장 |
| **GitOps 자동화** | update-manifest Job | 매니페스트 SHA 업데이트 완전 자동화 |

---

---

#### 5-3. update-manifest Job — 매니페스트 자동 업데이트

**역할**: `docker-build-push` 성공 후 `manifests/` 디렉토리의 `deployment.yaml`을  
새 이미지 Full SHA와 Short SHA 7자리로 자동 갱신하고 커밋합니다.

```yaml
update-manifest:
  needs: docker-build-push
  if: github.ref == 'refs/heads/main'
  permissions:
    contents: write          # ← 저장소 쓰기 권한 (커밋·push 필수)
  steps:
    - uses: actions/checkout@v4
      with:
        token: ${{ secrets.GITHUB_TOKEN }}

    - name: Update image tag
      run: |
        FULL_SHA=${{ github.sha }}
        SHORT_SHA=${FULL_SHA:0:7}
        APP=node-app
        FILE=manifests/${APP}/deployment.yaml

        # image 태그 갱신 (awk — sed보다 특수문자에 안전)
        awk -v sha="$FULL_SHA" -v app="$APP" '
          /image:.*/ && /app/ { sub(/:[^:]+$/, ":" sha) }
          { print }
        ' "$FILE" > tmp.yaml && mv tmp.yaml "$FILE"

        # APP_VERSION 갱신 (Short SHA 7자리)
        awk -v sha="$SHORT_SHA" '
          /APP_VERSION/{found=1} found && /value:/{sub(/".*"/, "\"" sha "\""); found=0}
          { print }
        ' "$FILE" > tmp.yaml && mv tmp.yaml "$FILE"

        # 멱등성: 변경 없으면 커밋 스킵
        git diff --cached --quiet && git diff --quiet && exit 0

        git config user.email 'github-actions@github.com'
        git config user.name 'github-actions'
        git add "$FILE"
        git commit -m "ci: update ${APP} image to ${SHORT_SHA}"

        # 동시 push 충돌 방지
        git pull --rebase origin main
        git push origin main
```

**핵심 설계 포인트**

| 포인트 | 설명 |
|--------|------|
| `permissions: contents: write` | 기본 GITHUB_TOKEN은 읽기 전용 — 쓰기 명시 필수 |
| `awk` 사용 | `sed`보다 특수문자(`/`, `:`) 처리가 안전하고 이식성이 높음 |
| `git diff --quiet && exit 0` | 같은 SHA 재push 시 빈 커밋 방지 (멱등성) |
| `git pull --rebase` | 3개 앱 동시 push 시 충돌 방지 |
| `if: github.ref == main` | PR push에서는 실행 안 됨 — main 병합 후에만 동작 |

> 💡 **path 필터 안전성**: `update-manifest` Job이 생성한 커밋은  
> `manifests/` 경로를 변경하므로 `app/` path 필터가 없는 CI가 재트리거될 수 있습니다.  
> 이를 방지하려면 커밋 메시지 `[skip ci]` 또는 `paths-ignore: manifests/`를 활용합니다.

---

## 🛠️ 실습 단계

> ⚠️ **사전 조건**: 저장소 Fork 완료, main 브랜치에 push 권한 있음

---

### Step 1: 워크플로우 파일 구조 확인

3개 앱에 대해 독립적인 워크플로우가 존재하는지 확인합니다.

```bash
cd ~/workspace/git-argocd-lecture

# 워크플로우 파일 목록
ls -la .github/workflows/

# 각 워크플로우의 트리거 경로 확인
grep -A 4 "paths:" .github/workflows/ci-java.yml
grep -A 4 "paths:" .github/workflows/ci-node.yml
grep -A 4 "paths:" .github/workflows/ci-python.yml
```

```
예상 출력:
total 24
-rw-r--r--  ci-java.yml
-rw-r--r--  ci-node.yml
-rw-r--r--  ci-python.yml

      - 'app/java-app/**'   ← java 변경만 트리거
      - 'app/node-app/**'   ← node 변경만 트리거
      - 'app/python-app/**' ← python 변경만 트리거
```

✅ **확인**: 3개 워크플로우 파일 존재, 각 앱별 독립 path 필터 적용

---

### Step 2: 저장소 권한(Permissions) 확인

> 💡 **중요**: 이 프로젝트의 CI는 `secrets.GITHUB_TOKEN`(자동 발급)을 사용합니다.  
> GHCR 푸시를 위해 별도 PAT를 등록할 필요가 없습니다.

GitHub 저장소 → **Settings** → **Actions** → **General** → **Workflow permissions** 확인:

```
필요 설정:
☑ Read and write permissions    ← packages:write 동작을 위해 필요
또는
개별 워크플로우의 permissions 블록으로 제어 (이미 설정됨)
```

```bash
# 워크플로우 내 permissions 확인
grep -A 3 "^permissions:" .github/workflows/ci-java.yml
```

```
예상 출력:
permissions:
  contents: read
  packages: write    ← GHCR 이미지 푸시 허용
```

✅ **확인**: `packages: write` 권한 설정 확인

---

### Step 3: 코드 변경 → main push → CI 트리거

node-app에 사소한 변경을 가해 CI가 트리거되는지 확인합니다.

**3-1. 변경 내용 작성**

```bash
cd ~/workspace/git-argocd-lecture

# node-app README에 빈 줄 추가 (의미 없는 변경으로 CI 트리거)
echo "" >> app/node-app/README.md

# 변경 확인
git diff app/node-app/README.md
```

**3-2. commit + push**

```bash
git add app/node-app/README.md
git commit -m "test: trigger CI for node-app"
git push origin main
```

```
예상 출력:
[main abc1234] test: trigger CI for node-app
Enumerating objects: 7, done.
...
To https://github.com/<username>/git-argocd-lecture.git
   xyz..abc  main -> main
```

**3-3. GitHub Actions 탭에서 실행 확인**

```bash
# 브라우저로 Actions 탭 열기
open https://github.com/$GITHUB_USERNAME/git-argocd-lecture/actions
```

```
예상 화면:
Workflows
  CI - Node.js App    ← 방금 push로 트리거된 워크플로우
  ● Running           ← 실행 중

CI - Java App    ← 미트리거 (node-app만 변경했으므로)
CI - Python App  ← 미트리거
```

✅ **확인**: `CI - Node.js App`만 트리거, Java/Python CI는 미실행

---

### Step 4: CI 실행 흐름 단계별 확인

Actions 탭 → `CI - Node.js App` 클릭 → 가장 최근 실행 클릭

```
  ┌──────────────┐  needs  ┌──────────────────┐  needs  ┌─────────────────┐
  │ Build & Test │ ──────> │ Docker Build &   │ ──────> │ Update Manifest │
  │  ✅ ~1분     │         │ Push  ✅ ~2~3분  │         │  ✅ ~30초       │
  └──────────────┘         └──────────────────┘         └─────────────────┘
        │                         │                              │
   테스트 통과                이미지 GHCR 등록             manifests/ SHA 갱신
                                                         ci: update 커밋 push
```

**각 Job의 Step 상세 확인**

```
Build & Test Job:
  ✅ Checkout source code
  ✅ Set up Node.js 18
  ✅ Install dependencies  (npm ci — package-lock.json 필수)
  ✅ Run tests             (4개 테스트 통과)

Docker Build & Push Job:
  ✅ Checkout source code
  ✅ Set up Docker Buildx
  ✅ Log in to GitHub Container Registry
  ✅ Extract Docker metadata
  ✅ Build and push Docker image
  ✅ Image digest summary

Update Manifest Job:  (main push 시에만)
  ✅ Checkout source code
  ✅ Update image tag      (awk로 Full SHA 갱신)
  ✅ git commit + push     (ci: update node-app image to abc1234)
```

```bash
# CI 완료 후 GHCR에 등록된 이미지 확인
open https://github.com/$GITHUB_USERNAME?tab=packages
```

```
예상 화면:
Packages
  node-app    Updated x minutes ago

태그 목록:
  latest
  sha-abc1234567890abcdef...  ← 40자 전체 SHA
```

✅ **확인**: `Build & Test` → `Docker Build & Push` → `Update Manifest` 3개 Job 모두 초록색(✅) 확인

---

### Step 5: PR 생성 → 빌드/테스트만 실행 확인

PR에서는 이미지 푸시 없이 빌드 + 테스트만 실행됨을 확인합니다.

**5-1. 새 브랜치 생성 + 변경**

```bash
cd ~/workspace/git-argocd-lecture

git checkout -b test/pr-ci-check
echo "# PR test" >> app/node-app/README.md
git add app/node-app/README.md
git commit -m "test: verify PR CI behavior"
git push origin test/pr-ci-check
```

**5-2. PR 생성**

```bash
# 브라우저에서 PR 생성
open https://github.com/$GITHUB_USERNAME/git-argocd-lecture/compare/test/pr-ci-check
```

```
GitHub 화면:
  base: main ← test/pr-ci-check → "Create pull request"
```

**5-3. PR CI 동작 확인**

PR 생성 후 Actions 탭 또는 PR 하단의 Checks 섹션 확인:

```
PR Checks:
  CI - Node.js App / Build & Test    ✅ (실행됨)
  CI - Node.js App / Docker Build & Push  ✅ (실행됨, 단 push=false)
```

**PR에서 이미지 푸시가 없는지 확인:**

```bash
# Docker Build & Push Job의 "Build and push Docker image" Step 로그 확인
# 로그에서 아래 내용 찾기:
# push: false  ← PR이므로 푸시 안 함
```

```
Step 로그 예시:
#19 exporting to image
#19 DONE 0.3s
(Pushed: false)   ← 이미지는 빌드만 되고 푸시되지 않음
```

```bash
# 브랜치 정리
git checkout main
git branch -d test/pr-ci-check
git push origin --delete test/pr-ci-check
```

✅ **확인**: PR 시 `Build & Test` 통과, 이미지 푸시는 발생하지 않음

---

### Step 6: CI 실패 시 로그 확인 방법

테스트 실패 시 CI가 중단되는 메커니즘을 이해합니다.

**6-1. 실패 시나리오 시뮬레이션 (선택 실습)**

```bash
# node-app 테스트에 일부러 실패 코드 삽입
cd ~/workspace/git-argocd-lecture/app/node-app

# 테스트 파일 임시 수정 (실제로 하지 않아도 됨 — 이해를 위한 예시)
# test/app.test.js 에서 assert.strictEqual(res.status, 200) → 999로 변경하면 실패
```

**6-2. 실패 로그 읽는 방법**

```
Actions 탭 → 실패한 워크플로우 → 실패한 Job 클릭
→ 실패한 Step 클릭 (빨간색 ✗)
→ 로그 펼쳐서 Error 메시지 확인

실패 로그 예시 (테스트 실패):
Error: Tests failed with exit code 1
✗ test: GET / should return 200 → Expected 200, got 999
```

**6-3. Job 의존성으로 인한 자동 중단**

```
Build & Test    ✗ 실패
Docker Build & Push  ⊘ 건너뜀 (needs: build-and-test + if: success())
```

```
💡 핵심: if: success() + needs: build-and-test 조합으로
   테스트 실패 시 이미지 푸시가 절대 실행되지 않습니다.
```

✅ **확인**: 실패 흐름 이해 — `Build & Test` 실패 → `Docker Build & Push` 자동 스킵

---

### Step 7: GHCR 푸시된 이미지 확인 및 관리

```bash
# 브라우저에서 패키지 확인
open https://github.com/$GITHUB_USERNAME?tab=packages

# 특정 패키지 상세 페이지 확인
open https://github.com/users/$GITHUB_USERNAME/packages/container/node-app
```

```
예상 화면:
node-app
  Recent tagged image versions
  sha-abc1234... (latest)  Published x minutes ago
  sha-xyz9876...           Published x days ago
```

**이미지 태그 형식 이해**

```bash
# CI에서 생성되는 태그 형식
# type=sha,format=long,prefix= → 전체 SHA (40자)
# 예: sha256:abc1234567890abcdef1234567890abcdef12345678

# type=raw,value=latest → latest 태그 (main push 시에만)

# 실제 pull 테스트
docker pull ghcr.io/$GITHUB_USERNAME/node-app:latest
```

```
예상 출력:
latest: Pulling from username/node-app
Digest: sha256:abc...
Status: Downloaded newer image for ghcr.io/username/node-app:latest
```

✅ **확인**: GHCR에 `latest` 태그와 SHA 태그 두 가지 이미지 존재

---

### Step 8: path 필터 독립성 확인

각 CI는 해당 앱 디렉토리 변경 시에만 트리거됨을 확인합니다.

```bash
cd ~/workspace/git-argocd-lecture

# docs/ 변경 → 어떤 CI도 트리거되지 않아야 함
echo "" >> README.md 2>/dev/null || touch README.md
git add README.md
git commit -m "docs: update readme (no CI trigger expected)"
git push origin main
```

```
예상 결과 (Actions 탭):
  CI - Java App    ← 트리거 없음
  CI - Node.js App ← 트리거 없음
  CI - Python App  ← 트리거 없음
```

✅ **확인**: `docs/` 또는 `manifests/` 변경 시 CI 워크플로우 미트리거

---

## ✅ 확인 체크리스트

- [ ] `CI - Node.js App` — `main` push 후 자동 트리거 확인
- [ ] `Build & Test` Job 성공 확인 (단위 테스트 4개 통과)
- [ ] `Docker Build & Push` Job 성공 확인
- [ ] GHCR에 `latest` + SHA 태그 이미지 2개 등록 확인
- [ ] `Update Manifest` Job 성공 확인 (main push 시에만 실행)
- [ ] `manifests/node-app/deployment.yaml` 이미지 태그가 새 Full SHA로 자동 갱신 확인
- [ ] 저장소에 `ci: update node-app image to <SHA>` 커밋 자동 생성 확인
- [ ] PR 생성 시 이미지 푸시 없이 빌드/테스트만 실행 확인 (`push: false`)
- [ ] `app/java-app/` 변경 없이 node-app 변경 시 `CI - Java App` 미트리거 확인
- [ ] CI 실패 시 `Docker Build & Push` + `Update Manifest` Job 자동 스킵 이해
- [ ] `secrets.GITHUB_TOKEN` (자동 발급)으로 GHCR 인증 및 매니페스트 커밋 가능함을 이해

---

**다음 단계**: [05. K8s 매니페스트 작성](05-k8s-manifests.md)
