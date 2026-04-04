# 프로젝트 진행 상황

> 최종 수정: 2026-04-04 (Phase 4 완료)  
> 작성/관리: docs 에이전트  
> 갱신 주기: 각 에이전트 작업 완료 시 orchestrator 지시에 따라 갱신

---

## 전체 진행 체크리스트

```
[x] Phase 1    : 선행 문서화 (docs)
[x] Phase 2-1  : 샘플 앱 개발 (app)
[x] Phase 2-2  : CI 워크플로우 (ci)
[x] Phase 2-3  : K8s 매니페스트 + ArgoCD 설정 (cd)
[x] Phase 3    : 인프라 구성 + 전체 배포 확인 (infra)
[x] Phase 4    : 교육 가이드 완성 (docs/simulator)   ✅ 최종 완료
[ ] Phase 5    : AWS EKS 전환 (infra)                ← 다음 단계 (문서 준비 완료)
```

> ✅ **Phase 1~4 전체 완료** — 문서화 / 앱 개발 / CI / CD / 인프라 / 교육 가이드 9개 완성

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

---

## Phase 2-2 — CI 워크플로우 (ci 에이전트) ✅ 완료

> 완료일: 2026-04-04 | 담당: ci | 생성 파일: 총 3개 (378줄)

| 파일 | 줄 수 | 설명 |
|------|-------|------|
| `.github/workflows/ci-java.yml` | 124줄 | Java 앱 CI 워크플로우 |
| `.github/workflows/ci-node.yml` | 127줄 | Node.js 앱 CI 워크플로우 |
| `.github/workflows/ci-python.yml` | 127줄 | Python 앱 CI 워크플로우 |

**주요 구현**: 2-Job 구조 (`build-and-test` → `docker-build-push`), path 필터 독립 트리거,  
PR 빌드/테스트만 실행, main push 시 GHCR `SHA+latest` 이중 태그, `type=gha` 캐시  
**요구사항**: FR-CI-001~007, NFR-CI-001~005, UC-CI-001~003 전항목 ✅ 충족

---

## Phase 2-3 — K8s 매니페스트 + ArgoCD (cd 에이전트) ✅ 완료

> 완료일: 2026-04-04 | 담당: cd | 생성 파일: 총 13개

| 파일 그룹 | 파일 수 | 내용 |
|---------|--------|------|
| K8s 매니페스트 (`manifests/*/`) | 9개 | Deployment + Service + Ingress × 3 앱 |
| ArgoCD CR (`argocd/applications/`) | 4개 | Application CR × 3 + AppProject |

**주요 구현**: 앱별 리소스 제한, liveness/readiness probe, RollingUpdate, non-root securityContext,  
imagePullSecrets, ArgoCD `auto-sync + prune + selfHeal + CreateNamespace`  
**요구사항**: FR-CD-001~006, FR-K8S-001~006, NFR-RES-001~004, NFR-HA-001~004 전항목 ✅ 충족

---

## Phase 3 — 인프라 구성 (infra 에이전트) ✅ 최종 완료

> 최종 완료일: 2026-04-04 | 담당: infra

### 구성 환경 정보

| 항목 | 값 |
|------|-----|
| K8s 배포판 | k3s `v1.34.6+k3s1` |
| 클러스터 컨텍스트 | `lima-rancher-desktop` |
| Ingress Controller | Nginx (Traefik 제거 후 대체) |
| Ingress External IP | `192.168.64.2` |
| ArgoCD UI | `https://192.168.64.2:31853` |
| 로컬 도메인 | `apps.local` (`192.168.64.2 apps.local` → `/etc/hosts`) |
| GitHub 저장소 | https://github.com/AnByoungHyun/git-argocd-lecture |
| GHCR Secret 이름 | `ghcr-secret` |
| 앱 네임스페이스 | `apps` |

### 수행 작업 (최초 구성)

| # | 작업 | 결과 |
|---|------|------|
| 1 | GitHub 저장소 생성 및 Push | ✅ |
| 2 | Rancher Desktop(k3s) v1.34.6+k3s1 클러스터 확인 | ✅ |
| 3 | Traefik 제거 → Nginx Ingress Controller 설치 (IP: 192.168.64.2) | ✅ |
| 4 | ArgoCD 설치 (7개 Pod Running) | ✅ |
| 5 | `apps` 네임스페이스 생성 | ✅ |
| 6 | `/etc/hosts` 도메인 안내 | ✅ |
| 7 | ArgoCD AppProject + Application CR 3개 적용 | ✅ |
| 8 | GHCR `ghcr-secret` 생성 및 ServiceAccount 패치 | ✅ |

### 후속 이슈 해소 기록 — 전체 7건 ✅ 해소 완료

| # | 이슈 | 원인 | 조치 에이전트 | 조치 내용 | 상태 |
|---|------|------|------------|---------|------|
| 1 | Java CI 빌드 실패 | `mvnw` (Maven Wrapper) 파일 누락 | app | Maven Wrapper 파일 추가 | ✅ 해소 |
| 2 | Node.js CI 빌드 실패 | `package-lock.json` 누락 (`npm ci` 실패) | app | `package-lock.json` 생성 | ✅ 해소 |
| 3 | Java CI 워크플로우 경로 오류 | `working-directory` 미설정 | ci | `working-directory` 옵션 추가 | ✅ 해소 |
| 4 | Java 테스트 실패 (404/405) | 에러 핸들러가 컨트롤러에 혼재 | app | `GlobalExceptionHandler` 별도 클래스로 분리 | ✅ 해소 |
| 5 | `mvnw` 스크립트 경로 버그 | `MAVEN_HOME` 계산 오류 | infra | `MAVEN_HOME` 계산 로직 수정 | ✅ 해소 |
| 6 | GHCR 레지스트리 경로 오타 | 이미지 경로 오타 | infra | 레지스트리 경로 수정 | ✅ 해소 |
| 7 | GHCR 이미지 pull 실패 | visibility 설정 확인 필요 | — | 이미 Public 상태 확인 (조치 불필요) | ✅ 해소 |

### 최종 배포 상태 — 3개 앱 전체 정상 확인 ✅

| 앱 | Pod 상태 | ArgoCD 상태 | 접근 URL |
|----|---------|-----------|---------|
| java-app | ✅ Running | Synced + Healthy | http://apps.local/java |
| node-app | ✅ Running | Synced + Healthy | http://apps.local/node |
| python-app | ✅ Running | Synced + Healthy | http://apps.local/python |

### 인프라 접근 정보 (운영 참조)

```
ArgoCD UI  : https://192.168.64.2:31853
java-app   : http://apps.local/java        (GET /, GET /health)
node-app   : http://apps.local/node        (GET /, GET /health)
python-app : http://apps.local/python      (GET /, GET /health)
GitHub     : https://github.com/AnByoungHyun/git-argocd-lecture
```

> `/etc/hosts`에 `192.168.64.2 apps.local` 등록 필요 (로컬 환경)

---

## Phase 4 — 교육 가이드 완성 (docs 에이전트) ✅ 완료

> 완료일: 2026-04-04 | 담당: docs (가이드 구조/이론/완성), simulator (실습 내용 검증)  
> 생성 파일: 총 9개 가이드, 약 7,146줄

### 완성된 가이드 목록

| 번호 | 파일 | 줄 수 | Mermaid | 이론 담당 | 실습 담당 |
|------|------|-------|---------|---------|---------|
| 00 | `guides/00-overview.md` | 178줄 | 4블록 | docs | — |
| 01 | `guides/01-prerequisites.md` | 668줄 | — | infra | simulator |
| 02 | `guides/02-sample-apps.md` | 805줄 | — | app | simulator |
| 03 | `guides/03-dockerize.md` | 791줄 | — | app | simulator |
| 04 | `guides/04-github-actions.md` | 774줄 | 3블록 | ci | simulator |
| 05 | `guides/05-k8s-manifests.md` | 831줄 | 2블록 | cd | simulator |
| 06 | `guides/06-rancher-argocd.md` | 874줄 | — | infra | simulator |
| 07 | `guides/07-gitops-deploy.md` | 895줄 | 6블록 | cd | simulator |
| 09 | `guides/09-aws-eks.md` | 1,330줄 | — | infra | simulator |
| **합계** | — | **7,146줄** | **15블록** | — | — |

### Phase 4 주요 성과

| 항목 | 수치 |
|------|------|
| 가이드 파일 수 | 9개 (00~07, 09) |
| 총 분량 | 약 7,146줄 |
| 실습 Step 수 | 71 Steps |
| 확인 체크포인트 | 70+ 항목 |
| Mermaid 다이어그램 | 15블록 (graph LR/TD, sequenceDiagram, stateDiagram-v2) |
| 가이드 간 링크 | 01→02→03→04→05→06→07→09 체인 완전 연결 |

### 이슈 사전 반영 (Phase 3 경험 반영)

| Phase 3 이슈 | 가이드 내 예방 조치 |
|-------------|-----------------|
| Java `mvnw` 누락 | `01-prerequisites.md` — Maven Wrapper 포함 확인 체크리스트 추가 |
| Node.js `package-lock.json` 누락 | `02-sample-apps.md` — `npm install` 후 lockfile 커밋 안내 |
| Java `GlobalExceptionHandler` 분리 | `02-sample-apps.md` — 에러 핸들러 구조 설명 포함 |
| GHCR visibility 설정 | `03-dockerize.md` — GHCR Public 설정 절차 명시 |
| CI `working-directory` 오류 | `04-github-actions.md` — working-directory 설정 예시 포함 |

### 가이드 상태

| 항목 | 상태 |
|------|------|
| 00. 전체 과정 소개 | ✅ 완성 (docs) |
| 01~07, 09 이론 섹션 | ✅ 완성 (각 전문 에이전트) |
| 01~07, 09 실습 섹션 | ✅ 완성 (simulator 검증) |
| 가이드 간 교차 링크 | ✅ 전 구간 확인 완료 |

---

## Phase 5 — AWS EKS 전환 (infra 에이전트) ⏳ 예정

> 담당 에이전트: infra  
> 선행 조건: Phase 4 시뮬레이션 검증 완료 후 진행  
> 참고 문서: [환경 전환 유즈케이스](../usecases/env-transition.md)

| 항목 | 상태 |
|------|------|
| EKS 클러스터 생성 (eksctl) | ⏳ 예정 |
| ArgoCD EKS 클러스터 등록 | ⏳ 예정 |
| Ingress 매니페스트 수정 (ALB 또는 Nginx) | ⏳ 예정 |
| imagePullSecret 적용 (EKS 환경) | ⏳ 예정 |
| EKS 배포 및 동작 검증 | ⏳ 예정 |

---

## 에이전트별 역할 요약

| 에이전트 | 담당 영역 | 현재 상태 |
|---------|---------|---------|
| docs | 문서화 전담 (`docs/`) | ✅ Phase 1 + Phase 4 완료 (가이드 9개 완성) |
| app | 샘플 앱 개발 (`app/`) | ✅ Phase 2-1 완료 / ✅ 후속 이슈 수정 완료 |
| ci | GitHub Actions CI (`.github/workflows/`) | ✅ Phase 2-2 완료 / ✅ 경로 오류 수정 완료 |
| cd | K8s 매니페스트 + ArgoCD (`manifests/`, `argocd/`) | ✅ Phase 2-3 완료 |
| infra | 인프라 구성 (로컬 k3s, AWS EKS) | ✅ Phase 3 최종 완료 / ⏳ Phase 5 예정 |
| simulator | 검증 및 결과 보고 | ⏳ Phase 4 예정 (진행 가능 상태) |
