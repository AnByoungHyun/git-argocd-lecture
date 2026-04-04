# 기능 요구사항 (Functional Requirements)

> 문서 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 작성: docs 에이전트

---

## 1. 멀티 언어 샘플 애플리케이션

### 1.1 개요

본 프로젝트는 GitOps 기반 CI/CD 파이프라인 검증을 목적으로 3가지 언어로 구성된 샘플 애플리케이션을 제공한다.

| 앱 | 언어 / 프레임워크 | 디렉토리 |
|----|------------------|---------|
| java-app | Java 17 / Spring Boot 3.x | `app/java-app/` |
| node-app | Node.js 18+ / Express | `app/node-app/` |
| python-app | Python 3.11+ / FastAPI | `app/python-app/` |

---

### 1.2 공통 기능 요구사항 (각 앱 공통)

#### FR-001 헬스체크 엔드포인트
- **경로**: `GET /health`
- **응답**: HTTP 200 OK
- **응답 바디**:
  ```json
  {
    "status": "ok",
    "app": "<앱 이름>",
    "version": "<버전>"
  }
  ```
- **목적**: K8s liveness/readiness probe 및 ArgoCD 배포 상태 확인

#### FR-002 기본 API 엔드포인트
- **경로**: `GET /`
- **응답**: HTTP 200 OK
- **응답 바디**: 앱 이름과 환경 정보를 포함한 JSON 또는 텍스트
- **목적**: 배포 후 동작 확인용

#### FR-003 Dockerfile
- 각 앱 루트에 `Dockerfile` 포함 (멀티스테이지 빌드 권장)
- GitHub Container Registry(GHCR) 또는 DockerHub에 푸시 가능한 이미지 생성
- 이미지 태그는 git SHA 기반으로 관리

---

### 1.3 Java 앱 (java-app)

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-JAVA-001 | `GET /health` — 헬스체크 응답 반환 | 필수 |
| FR-JAVA-002 | `GET /` — 앱 기본 정보 응답 | 필수 |
| FR-JAVA-003 | Spring Boot Actuator 비활성화 또는 최소 설정 (Dockerfile 경량화) | 권장 |
| FR-JAVA-004 | 멀티스테이지 Dockerfile: build(Maven/Gradle) → runtime(JRE) | 필수 |
| FR-JAVA-005 | 포트: 8080 | 필수 |

---

### 1.4 Node.js 앱 (node-app)

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-NODE-001 | `GET /health` — 헬스체크 응답 반환 | 필수 |
| FR-NODE-002 | `GET /` — 앱 기본 정보 응답 | 필수 |
| FR-NODE-003 | Express 프레임워크 사용 | 필수 |
| FR-NODE-004 | `package.json`에 `start` 스크립트 정의 | 필수 |
| FR-NODE-005 | Dockerfile: node:18-alpine 기반 | 필수 |
| FR-NODE-006 | 포트: 3000 | 필수 |

---

### 1.5 Python 앱 (python-app)

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-PY-001 | `GET /health` — 헬스체크 응답 반환 | 필수 |
| FR-PY-002 | `GET /` — 앱 기본 정보 응답 | 필수 |
| FR-PY-003 | FastAPI + Uvicorn 사용 | 필수 |
| FR-PY-004 | `requirements.txt` 또는 `pyproject.toml` 의존성 정의 | 필수 |
| FR-PY-005 | Dockerfile: python:3.11-slim 기반 | 필수 |
| FR-PY-006 | 포트: 8000 | 필수 |

---

## 2. GitHub Actions CI 파이프라인

### 2.1 개요

각 앱별 GitHub Actions 워크플로우를 구성하여 코드 변경 시 자동으로 빌드, 테스트, 이미지 빌드/푸시를 수행한다.

### 2.2 CI 파이프라인 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-CI-001 | `main` 브랜치 push 또는 PR 시 파이프라인 트리거 | 필수 |
| FR-CI-002 | 소스 코드 빌드 및 단위 테스트 실행 | 필수 |
| FR-CI-003 | Docker 이미지 빌드 | 필수 |
| FR-CI-004 | git SHA 태그로 이미지 레지스트리(GHCR 또는 DockerHub)에 푸시 | 필수 |
| FR-CI-005 | `latest` 태그 함께 푸시 (`main` 브랜치 push 시에만) | 권장 |
| FR-CI-006 | 3개 앱 파이프라인 독립 실행 (각 앱 변경 시 해당 앱만 트리거) | 권장 |
| FR-CI-007 | 빌드 실패 시 이미지 푸시 차단 | 필수 |

### 2.3 워크플로우 파일 위치

```
.github/workflows/
  ci-java.yml
  ci-node.yml
  ci-python.yml
```

---

## 3. ArgoCD 기반 GitOps CD

### 3.1 개요

Git 저장소의 K8s 매니페스트 변경을 ArgoCD가 감지하여 클러스터에 자동 배포하는 GitOps 방식을 채택한다.

### 3.2 CD 파이프라인 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-CD-001 | ArgoCD Application CR로 각 앱 배포 정의 | 필수 |
| FR-CD-002 | Git 저장소의 `manifests/` 경로 변경 감지 → 자동 동기화 | 필수 |
| FR-CD-003 | CI에서 이미지 태그 업데이트 후 CD 트리거 (이미지 태그 기반 배포) | 필수 |
| FR-CD-004 | 자동 동기화(auto-sync) 활성화 | 필수 |
| FR-CD-005 | 동기화 실패 시 ArgoCD UI에서 상태 확인 가능 | 필수 |
| FR-CD-006 | Self-healing 옵션 활성화 (수동 변경 감지 후 Git 상태로 복원) | 권장 |

---

## 4. K8s 매니페스트

### 4.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-K8S-001 | 각 앱별 `Deployment` 매니페스트 작성 | 필수 |
| FR-K8S-002 | 각 앱별 `Service` 매니페스트 작성 (ClusterIP 기본) | 필수 |
| FR-K8S-003 | `Ingress` 매니페스트 작성 (경로 기반 라우팅) | 필수 |
| FR-K8S-004 | Helm 미사용, plain YAML만 사용 | 필수 |
| FR-K8S-005 | liveness/readiness probe 설정 (`/health` 엔드포인트 활용) | 필수 |
| FR-K8S-006 | `requests` / `limits` 리소스 제한 설정 | 필수 |

### 4.2 매니페스트 디렉토리 구조

```
manifests/
  java-app/
    deployment.yaml
    service.yaml
    ingress.yaml
  node-app/
    deployment.yaml
    service.yaml
    ingress.yaml
  python-app/
    deployment.yaml
    service.yaml
    ingress.yaml
```

---

## 5. 배포 환경 전환

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-ENV-001 | 로컬 Rancher Desktop(k3s)에서 전체 파이프라인 검증 | 필수 |
| FR-ENV-002 | 검증 완료 후 AWS EKS 클러스터로 전환 | 필수 |
| FR-ENV-003 | 환경별 차이를 최소화하여 이식성 확보 | 권장 |
