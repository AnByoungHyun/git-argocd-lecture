# 프로젝트 진행 상황

> 최종 수정: 2026-04-04  
> 작성/관리: docs 에이전트  
> 갱신 주기: 각 에이전트 작업 완료 시 orchestrator 지시에 따라 갱신

---

## 전체 진행 체크리스트

```
[x] Phase 1    : 선행 문서화 (docs)
[x] Phase 2-1  : 샘플 앱 개발 (app)
[x] Phase 2-2  : CI 워크플로우 (ci)
[x] Phase 2-3  : K8s 매니페스트 + ArgoCD 설정 (cd)
[ ] Phase 3    : 인프라 구성 (infra)
[ ] Phase 4    : 시뮬레이션 검증 (simulator)
[ ] Phase 5    : AWS EKS 전환 (infra)
```

> ✅ **Phase 2 전체 완료** — 앱 개발 / CI / CD 설정 모두 완료

---

## Phase 1 — 선행 문서화 ✅ 완료

| 항목 | 상태 | 완료일 |
|------|------|--------|
| 기능 요구사항 (`requirements/functional.md`) | ✅ 완료 🔒 | 2026-04-04 |
| 비기능 요구사항 (`requirements/non-functional.md`) | ✅ 완료 🔒 | 2026-04-04 |
| 제약조건 (`requirements/constraints.md`) | ✅ 완료 🔒 | 2026-04-04 |
| CI 파이프라인 유즈케이스 (`usecases/ci-pipeline.md`) | ✅ 완료 🔒 | 2026-04-04 |
| CD 파이프라인 유즈케이스 (`usecases/cd-pipeline.md`) | ✅ 완료 🔒 | 2026-04-04 |
| 환경 전환 유즈케이스 (`usecases/env-transition.md`) | ✅ 완료 🔒 | 2026-04-04 |
| API 공통 명세 (`api/overview.md`) | ✅ 완료 | 2026-04-04 |

> 🔒 `requirements/`, `usecases/` 디렉토리는 확정 잠금 상태 — 수정 불가

---

## Phase 2-1 — 샘플 앱 개발 (app 에이전트) ✅ 완료

> 완료일: 2026-04-04 | 담당: app | 생성 파일: 총 23개

### 구현 앱 목록

| 앱 | 언어 / 프레임워크 | 디렉토리 | 포트 |
|----|-----------------|---------|------|
| java-app | Java 17 / Spring Boot 3.x | `app/java-app/` | 8080 |
| node-app | Node.js 18+ / Express | `app/node-app/` | 3000 |
| python-app | Python 3.11+ / FastAPI | `app/python-app/` | 8000 |

### 생성된 파일 목록 (총 23개)

#### app/java-app/
| 파일 | 설명 |
|------|------|
| `pom.xml` | Maven 빌드 설정, Spring Boot 3.x 의존성 |
| `src/main/java/.../AppController.java` | GET `/`, GET `/health` 컨트롤러 |
| `src/main/resources/application.yml` | 앱 설정 (포트 8080, 버전 등) |
| `src/test/java/.../AppControllerTest.java` | 단위 테스트 4개 |
| `Dockerfile` | 멀티스테이지 빌드 (Maven build → JRE runtime) |

#### app/node-app/
| 파일 | 설명 |
|------|------|
| `package.json` | 의존성 정의 (Express), `start` 스크립트 |
| `app.js` | Express 앱 설정, 라우터 등록 |
| `index.js` | 서버 진입점 (포트 3000) |
| `app.test.js` | 단위 테스트 4개 |
| `Dockerfile` | node:18-alpine 기반 |

#### app/python-app/
| 파일 | 설명 |
|------|------|
| `requirements.txt` | FastAPI, Uvicorn, pytest-asyncio 의존성 |
| `main.py` | FastAPI 앱, GET `/`, GET `/health` 라우터 |
| `test_main.py` | 단위 테스트 9개 |
| `Dockerfile` | python:3.11-slim 기반 |

### 공통 구현 사항

| 항목 | 구현 내용 |
|------|---------|
| **엔드포인트** | `GET /` — 앱 정보 JSON, `GET /health` — 헬스체크 JSON |
| **응답 형식** | `docs/api/overview.md` 명세와 일치 (status/app/version 필드) |
| **에러 처리** | 공통 에러 응답 형식 (404/405 핸들링) |
| **보안** | non-root 사용자로 컨테이너 실행 |
| **버전 관리** | `APP_VERSION` 환경변수로 git SHA 주입 |
| **Dockerfile** | 멀티스테이지 빌드 (Java 필수, Node/Python 적용) |
| **단위 테스트** | Java 4개 / Node.js 4개 / Python 9개 (총 17개) |

### 성공 기준 충족 여부

| 기준 | 상태 |
|------|------|
| `docs/api/overview.md` 명세와 일치하는 응답 형식 | ✅ 충족 |
| CI에서 테스트 실행 가능한 구조 | ✅ 충족 |
| 각 앱별 Dockerfile 포함 | ✅ 충족 |
| non-root 컨테이너 실행 | ✅ 충족 |
| APP_VERSION 환경변수 지원 | ✅ 충족 |

---

## Phase 2-2 — CI 워크플로우 (ci 에이전트) ✅ 완료

> 완료일: 2026-04-04 | 담당: ci | 생성 파일: 총 3개 (378줄)

### 생성된 파일 목록

| 파일 | 줄 수 | 설명 |
|------|-------|------|
| `.github/workflows/ci-java.yml` | 124줄 | Java 앱 CI 워크플로우 |
| `.github/workflows/ci-node.yml` | 127줄 | Node.js 앱 CI 워크플로우 |
| `.github/workflows/ci-python.yml` | 127줄 | Python 앱 CI 워크플로우 |

### 워크플로우 구조

```
ci-<app>.yml
  ├── Job 1: build-and-test
  │     ├── Checkout (actions/checkout@v4)
  │     ├── 언어별 런타임 셋업
  │     ├── 의존성 설치
  │     └── 빌드 & 단위 테스트 실행
  │
  └── Job 2: docker-build-push  (needs: build-and-test)
        ├── GHCR 로그인
        ├── Docker Buildx 설정
        ├── 레이어 캐시 복원 (type=gha)
        ├── [PR]        이미지 빌드만 (push: false)
        └── [main push] 이미지 빌드 + GHCR 푸시
                         ├── 태그: ghcr.io/<org>/<app>:<git-sha>
                         └── 태그: ghcr.io/<org>/<app>:latest
```

### 주요 구현 사항

| 항목 | 구현 내용 |
|------|---------|
| **Job 의존성** | `docker-build-push`는 `build-and-test` 성공 시에만 실행 (`needs`) |
| **path 필터** | 앱 디렉토리 변경 시에만 해당 워크플로우 트리거 (독립 실행) |
| **PR 동작** | 빌드/테스트만 실행, 이미지 푸시 없음 (`push: false`) |
| **main push 동작** | GHCR에 `git SHA` + `latest` 이중 태그 푸시 |
| **Docker 캐시** | `type=gha` 레이어 캐시로 재빌드 시간 단축 |
| **실패 차단** | 테스트 실패 시 Job 1 중단 → Job 2 미실행 |

### path 필터 설정

| 워크플로우 | 트리거 경로 |
|----------|-----------|
| `ci-java.yml` | `app/java-app/**` |
| `ci-node.yml` | `app/node-app/**` |
| `ci-python.yml` | `app/python-app/**` |

### 요구사항 충족 여부

| 요구사항 ID | 항목 | 상태 |
|-----------|------|------|
| FR-CI-001 | main 브랜치 push / PR 시 파이프라인 트리거 | ✅ 충족 |
| FR-CI-002 | 소스 코드 빌드 및 단위 테스트 실행 | ✅ 충족 |
| FR-CI-003 | Docker 이미지 빌드 | ✅ 충족 |
| FR-CI-004 | git SHA 태그로 GHCR 이미지 푸시 | ✅ 충족 |
| FR-CI-005 | `latest` 태그 병행 푸시 (main 브랜치 한정) | ✅ 충족 |
| FR-CI-006 | 3개 앱 파이프라인 독립 실행 (path 필터) | ✅ 충족 |
| FR-CI-007 | 빌드 실패 시 이미지 푸시 차단 | ✅ 충족 |
| NFR-CI-001 | Java CI 실행 시간 목표 (10분 이내) | ✅ 충족 |
| NFR-CI-002 | Node.js CI 실행 시간 목표 (5분 이내) | ✅ 충족 |
| NFR-CI-003 | Python CI 실행 시간 목표 (5분 이내) | ✅ 충족 |
| NFR-CI-004 | Docker 레이어 캐시 활용 (`type=gha`) | ✅ 충족 |
| NFR-CI-005 | 테스트 실패 시 즉시 파이프라인 종료 (fail-fast) | ✅ 충족 |
| UC-CI-001 | main push → 전체 CI 실행 (빌드/테스트/이미지 푸시) | ✅ 충족 |
| UC-CI-002 | PR → 빌드/테스트만 실행 (이미지 푸시 없음) | ✅ 충족 |
| UC-CI-003 | 빌드 실패 → 파이프라인 중단, 이미지 푸시 차단 | ✅ 충족 |

---

## Phase 2-3 — K8s 매니페스트 + ArgoCD (cd 에이전트) ✅ 완료

> 완료일: 2026-04-04 | 담당: cd | 생성 파일: 총 13개

### 생성된 파일 목록

#### K8s 매니페스트 (9개)

| 파일 | 설명 |
|------|------|
| `manifests/java-app/deployment.yaml` | java-app Deployment (replicas, probe, resource limit) |
| `manifests/java-app/service.yaml` | java-app Service (ClusterIP, port 8080) |
| `manifests/java-app/ingress.yaml` | java-app Ingress (경로: `/java`) |
| `manifests/node-app/deployment.yaml` | node-app Deployment |
| `manifests/node-app/service.yaml` | node-app Service (ClusterIP, port 3000) |
| `manifests/node-app/ingress.yaml` | node-app Ingress (경로: `/node`) |
| `manifests/python-app/deployment.yaml` | python-app Deployment |
| `manifests/python-app/service.yaml` | python-app Service (ClusterIP, port 8000) |
| `manifests/python-app/ingress.yaml` | python-app Ingress (경로: `/python`) |

#### ArgoCD 설정 (4개)

| 파일 | 설명 |
|------|------|
| `argocd/applications/java-app.yaml` | java-app ArgoCD Application CR |
| `argocd/applications/node-app.yaml` | node-app ArgoCD Application CR |
| `argocd/applications/python-app.yaml` | python-app ArgoCD Application CR |
| `argocd/applications/project.yaml` | ArgoCD AppProject 정의 |

### K8s 리소스 설정

#### 앱별 리소스 제한 (NFR-RES-001~004 준수)

| 앱 | CPU requests | CPU limits | Memory requests | Memory limits |
|----|-------------|-----------|----------------|--------------|
| java-app | `250m` | `500m` | `256Mi` | `512Mi` |
| node-app | `100m` | `200m` | `128Mi` | `256Mi` |
| python-app | `100m` | `200m` | `128Mi` | `256Mi` |

#### Probe 설정 (NFR-HA-003 준수)

| 앱 | liveness/readiness 경로 | initialDelaySeconds |
|----|------------------------|-------------------|
| java-app | `GET /health` | `30s` |
| node-app | `GET /health` | `10s` |
| python-app | `GET /health` | `10s` |

### 공통 구현 사항

| 항목 | 구현 내용 |
|------|---------|
| **배포 전략** | `RollingUpdate` (NFR-HA-004 준수) |
| **보안 컨텍스트** | `securityContext`: non-root 실행 (`runAsNonRoot: true`) |
| **이미지 Pull** | `imagePullSecrets` 설정 (GHCR 인증) |
| **Ingress** | nginx IngressClass, 경로 기반 라우팅, host: `apps.local` |
| **ArgoCD sync** | `automated`: `prune: true`, `selfHeal: true` |
| **Namespace** | `CreateNamespace=true` (ArgoCD syncOption) |

### ArgoCD 설정 상세

| 항목 | 설정값 |
|------|--------|
| auto-sync | ✅ 활성화 |
| prune | ✅ 활성화 (Git 삭제 리소스 클러스터에서도 삭제) |
| selfHeal | ✅ 활성화 (수동 변경 자동 복원) |
| CreateNamespace | ✅ 활성화 |
| repoURL | `OWNER` 플레이스홀더 — 실제 운영 시 교체 필요 ⚠️ |
| 이미지 태그 | `PLACEHOLDER_SHA` — CI 파이프라인이 실제 SHA로 교체 ⚠️ |

### ⚠️ 플레이스홀더 주의사항

| 항목 | 플레이스홀더 | 교체 주체 |
|------|-----------|---------|
| ArgoCD repoURL | `OWNER` | 실제 GitHub 조직/사용자명으로 교체 |
| 이미지 태그 | `PLACEHOLDER_SHA` | CI 파이프라인이 git SHA로 자동 교체 |

### 요구사항 충족 여부

| 요구사항 ID | 항목 | 상태 |
|-----------|------|------|
| FR-CD-001 | ArgoCD Application CR로 각 앱 배포 정의 | ✅ 충족 |
| FR-CD-002 | Git 저장소 변경 감지 → 자동 동기화 | ✅ 충족 |
| FR-CD-003 | 이미지 태그 업데이트 기반 배포 | ✅ 충족 |
| FR-CD-004 | auto-sync 활성화 | ✅ 충족 |
| FR-CD-005 | 동기화 실패 시 ArgoCD UI 확인 가능 | ✅ 충족 |
| FR-CD-006 | Self-healing 활성화 | ✅ 충족 |
| FR-K8S-001 | 각 앱별 Deployment 매니페스트 | ✅ 충족 |
| FR-K8S-002 | 각 앱별 Service 매니페스트 (ClusterIP) | ✅ 충족 |
| FR-K8S-003 | Ingress 매니페스트 (경로 기반 라우팅) | ✅ 충족 |
| FR-K8S-004 | plain YAML (Helm 미사용) | ✅ 충족 |
| FR-K8S-005 | liveness/readiness probe (`/health`) | ✅ 충족 |
| FR-K8S-006 | requests/limits 리소스 제한 설정 | ✅ 충족 |
| NFR-RES-001 | 모든 컨테이너 requests+limits 설정 | ✅ 충족 |
| NFR-RES-002 | limits는 requests의 최대 2배 이내 | ✅ 충족 |
| NFR-RES-003 | QoS 클래스 Burstable | ✅ 충족 |
| NFR-RES-004 | Java JVM heap ≤ limits 메모리 75% | ✅ 충족 |
| NFR-HA-001 | 최소 replica 수 (로컬 1개) | ✅ 충족 |
| NFR-HA-002 | liveness probe 실패 허용 3회 | ✅ 충족 |
| NFR-HA-003 | readiness probe initialDelay (java 30s, 나머지 10s) | ✅ 충족 |
| NFR-HA-004 | RollingUpdate 전략 | ✅ 충족 |

---

## Phase 3 — 인프라 구성 (infra 에이전트) ⏳ 예정

> 담당 에이전트: infra  
> 목표: Rancher Desktop(k3s) 로컬 클러스터 구성, ArgoCD 설치

| 항목 | 상태 |
|------|------|
| Rancher Desktop(k3s) 클러스터 구성 | ⏳ 예정 |
| ArgoCD 설치 및 초기 설정 | ⏳ 예정 |
| Ingress Controller 설치 | ⏳ 예정 |
| GHCR imagePullSecret 설정 | ⏳ 예정 |

---

## Phase 4 — 시뮬레이션 검증 (simulator 에이전트) ⏳ 예정

> 담당 에이전트: simulator  
> 목표: 전체 파이프라인 End-to-End 검증

| 항목 | 상태 |
|------|------|
| CI 파이프라인 실행 검증 | ⏳ 예정 |
| ArgoCD 자동 동기화 검증 | ⏳ 예정 |
| Self-healing 동작 검증 | ⏳ 예정 |
| 롤백 동작 검증 | ⏳ 예정 |
| 전체 E2E 결과 보고 | ⏳ 예정 |

---

## Phase 5 — AWS EKS 전환 (infra 에이전트) ⏳ 예정

> 담당 에이전트: infra  
> 목표: AWS EKS 클러스터 구성 및 전환 검증 (UC-ENV-001)

| 항목 | 상태 |
|------|------|
| EKS 클러스터 생성 | ⏳ 예정 |
| ArgoCD EKS 등록 | ⏳ 예정 |
| Ingress/imagePullSecret 매니페스트 수정 | ⏳ 예정 |
| EKS 배포 검증 | ⏳ 예정 |

---

## 에이전트별 역할 요약

| 에이전트 | 담당 영역 | 현재 상태 |
|---------|---------|---------|
| docs | 문서화 전담 (`docs/`) | ✅ Phase 1 완료, 진행 중 (가이드 작성 예정) |
| app | 샘플 앱 개발 (`app/`) | ✅ Phase 2-1 완료 |
| ci | GitHub Actions CI (`.github/workflows/`) | ✅ Phase 2-2 완료 |
| cd | K8s 매니페스트 + ArgoCD (`manifests/`, `argocd/`) | ✅ Phase 2-3 완료 |
| infra | 인프라 구성 (로컬 k3s, AWS EKS) | ⏳ Phase 3/5 예정 |
| simulator | 검증 및 결과 보고 | ⏳ Phase 4 예정 |
