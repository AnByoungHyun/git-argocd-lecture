# 유즈케이스: CI 파이프라인 (GitHub Actions)

> 문서 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 작성: docs 에이전트  
> 관련 요구사항: [기능 요구사항 §2](../requirements/functional.md), [제약조건 §3](../requirements/constraints.md)

---

## 개요

GitHub Actions를 이용한 CI(지속적 통합) 파이프라인의 주요 유즈케이스를 정의한다.  
빌드·테스트·이미지 푸시의 3단계로 구성되며, 트리거 조건에 따라 동작 범위가 달라진다.

### 액터 및 시스템

| 구분 | 설명 |
|------|------|
| **주 액터** | 개발자 (Developer) |
| **시스템** | GitHub, GitHub Actions, GitHub Container Registry (GHCR) |
| **관련 산출물** | 컨테이너 이미지 (`ghcr.io/<org>/<app>:<git-sha>`) |

---

## UC-CI-001: main 브랜치 push → 빌드/테스트/이미지 푸시

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-CI-001 |
| **이름** | main 브랜치 push 시 전체 CI 실행 |
| **액터** | 개발자 |
| **트리거** | `main` 브랜치에 git push 발생 |
| **우선순위** | 필수 |

### 사전조건

- 개발자가 GitHub 저장소에 push 권한 보유
- GitHub Actions 워크플로우 파일 (`.github/workflows/ci-<app>.yml`) 존재
- GHCR 인증 토큰이 GitHub Actions Secret에 등록 (`GHCR_TOKEN` 또는 `GITHUB_TOKEN`)
- 변경된 파일이 해당 앱 디렉토리(`app/<app-name>/`) 하위에 존재

### 주요 흐름

```
1. 개발자가 로컬에서 코드 수정 후 main 브랜치에 push
2. GitHub이 push 이벤트 감지 → 해당 앱의 워크플로우 트리거
   (path filter: app/java-app/** → ci-java.yml만 실행)
3. GitHub Actions Runner 시작 (ubuntu-latest)
4. [Checkout] 저장소 코드 체크아웃 (actions/checkout@v4)
5. [Build & Test]
   ├── Java:   ./mvnw test 또는 ./gradlew test
   ├── Node:   npm ci && npm test
   └── Python: pip install -r requirements.txt && pytest
6. 테스트 성공 확인 → 실패 시 UC-CI-003으로 분기
7. [Docker Build] Dockerfile 기반 이미지 빌드
   - 태그: ghcr.io/<org>/<app>:<git-sha> (7자리)
   - 태그: ghcr.io/<org>/<app>:latest (main 브랜치 한정)
8. [Docker Push] GHCR에 이미지 푸시
9. [Notify] 워크플로우 완료 상태 GitHub PR/Commit에 표시
10. (선택) CD 트리거: manifests/ 내 이미지 태그 업데이트 커밋 생성
```

### 사후조건 (성공 시)

- GHCR에 `<git-sha>` 태그 이미지 등록
- GHCR에 `latest` 태그 이미지 갱신
- GitHub Commit 상태에 ✅ 표시
- (선택) `manifests/<app>/deployment.yaml`의 이미지 태그가 새 SHA로 업데이트

### 예외 흐름

| 단계 | 예외 상황 | 처리 |
|------|---------|------|
| 5 | 테스트 실패 | → UC-CI-003으로 분기 |
| 7 | Dockerfile 오류 | 빌드 실패, 파이프라인 중단 |
| 8 | GHCR 인증 실패 | 푸시 실패, 파이프라인 중단, Secret 설정 확인 필요 |

### 워크플로우 예시 구조

```yaml
# .github/workflows/ci-java.yml
on:
  push:
    branches: [main]
    paths:
      - 'app/java-app/**'

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build & Test
        run: ./mvnw -f app/java-app/pom.xml test
      - name: Docker Build & Push
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: |
            ghcr.io/${{ github.repository_owner }}/java-app:${{ github.sha }}
            ghcr.io/${{ github.repository_owner }}/java-app:latest
```

---

## UC-CI-002: PR 생성 시 → 빌드/테스트만 실행 (이미지 푸시 없음)

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-CI-002 |
| **이름** | Pull Request CI (이미지 푸시 제외) |
| **액터** | 개발자 |
| **트리거** | `main` 브랜치 대상 Pull Request 생성 또는 업데이트 |
| **우선순위** | 필수 |

### 사전조건

- PR이 `main` 브랜치 대상으로 생성됨
- GitHub Actions 워크플로우에 `pull_request` 트리거 설정됨
- GHCR 인증 정보 불필요 (푸시 없음)

### 주요 흐름

```
1. 개발자가 feature 브랜치에서 main 대상으로 PR 생성 또는 커밋 추가
2. GitHub이 pull_request 이벤트 감지 → 워크플로우 트리거
3. GitHub Actions Runner 시작
4. [Checkout] PR 브랜치 코드 체크아웃
5. [Build & Test]
   ├── Java:   ./mvnw test
   ├── Node:   npm ci && npm test
   └── Python: pytest
6. 테스트 성공 → PR에 ✅ Check 표시
   테스트 실패 → PR에 ❌ Check 표시 (머지 차단)
7. [Docker Build Only] 이미지 빌드만 수행 (push: false)
   - 빌드 가능 여부 검증 목적
8. ❌ 이미지 레지스트리 푸시 없음 (보안 + 불필요한 이미지 생성 방지)
```

### 사후조건 (성공 시)

- PR에 CI 통과 상태 ✅ 표시
- 이미지 레지스트리에 변경 없음
- 리뷰어가 머지 가능 여부 판단 가능

### 사후조건 (실패 시)

- PR에 CI 실패 상태 ❌ 표시
- 브랜치 보호 규칙으로 머지 차단 (설정 시)

### UC-CI-001과 비교

| 항목 | UC-CI-001 (main push) | UC-CI-002 (PR) |
|------|----------------------|----------------|
| 트리거 | `push` to `main` | `pull_request` |
| 테스트 | ✅ 실행 | ✅ 실행 |
| 이미지 빌드 | ✅ 실행 | ✅ 실행 (검증용) |
| 이미지 푸시 | ✅ 실행 | ❌ 미실행 |
| 이미지 태그 업데이트 | ✅ (선택) | ❌ |

---

## UC-CI-003: 빌드/테스트 실패 → 파이프라인 중단, 이미지 푸시 차단

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-CI-003 |
| **이름** | CI 실패 처리 및 이미지 푸시 차단 |
| **액터** | GitHub Actions (자동), 개발자 (대응) |
| **트리거** | 빌드 또는 테스트 단계에서 오류 발생 |
| **우선순위** | 필수 |

### 사전조건

- UC-CI-001 또는 UC-CI-002 실행 중
- 빌드 또는 테스트 Step에서 exit code ≠ 0 반환

### 주요 흐름

```
1. 빌드 또는 테스트 Step 실패 (exit code ≠ 0)
2. GitHub Actions: 이후 모든 Step 즉시 중단 (fail-fast)
3. [차단] Docker 이미지 빌드 Step 실행 안 됨
4. [차단] GHCR 이미지 푸시 Step 실행 안 됨
5. [차단] manifests/ 이미지 태그 업데이트 Step 실행 안 됨
6. GitHub Commit/PR에 ❌ 상태 표시
7. 개발자에게 이메일/알림 전송 (GitHub 기본 알림)
8. 개발자가 Actions 로그 확인 → 원인 분석 → 수정 후 재push
```

### 사후조건

- 이미지 레지스트리에 실패 버전 이미지 없음 (오염 방지)
- manifests/ 이미지 태그 변경 없음 (CD 트리거 없음)
- 기존 배포 상태 유지 (영향 없음)

### 실패 유형별 대응

| 실패 유형 | 증상 | 개발자 대응 |
|---------|------|-----------|
| 컴파일/빌드 오류 | Build step 실패 | 소스 코드 수정 후 재push |
| 단위 테스트 실패 | Test step 실패 | 테스트 수정 또는 로직 수정 후 재push |
| Dockerfile 오류 | Docker build step 실패 | Dockerfile 수정 후 재push |
| 의존성 오류 | 패키지 설치 step 실패 | `pom.xml`/`package.json`/`requirements.txt` 수정 |

### 실패 감지 보장 메커니즘

```yaml
# GitHub Actions는 기본적으로 step 실패 시 이후 step 중단
# 명시적으로 보장하려면:
jobs:
  build:
    steps:
      - name: Test
        run: ./mvnw test   # 실패 시 이후 step 실행 안 됨

      - name: Docker Push   # Test 실패 시 이 step은 실행되지 않음
        if: success()       # 명시적 조건 추가 권장
        uses: docker/build-push-action@v5
        with:
          push: true
```

---

## 유즈케이스 관계도

```
개발자
  │
  ├─[main push]──→ UC-CI-001: 전체 CI ──→ 성공: 이미지 푸시 + (선택) CD 트리거
  │                                  └──→ 실패: UC-CI-003 (이미지 푸시 차단)
  │
  └─[PR 생성/업데이트]──→ UC-CI-002: 빌드+테스트만 ──→ 성공: PR Check ✅
                                                  └──→ 실패: PR Check ❌ (머지 차단)
```
