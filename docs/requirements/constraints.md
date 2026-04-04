# 제약조건 (Constraints)

> 문서 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 작성: docs 에이전트

---

## 1. 언어 및 프레임워크

| 앱 | 언어 | 프레임워크 | 최소 버전 | 비고 |
|----|-----|----------|---------|------|
| java-app | Java | Spring Boot | Java 17+, Spring Boot 3.x | LTS 버전 사용 |
| node-app | Node.js | Express | Node.js 18+ | LTS 버전 사용 |
| python-app | Python | FastAPI + Uvicorn | Python 3.11+ | |

---

## 2. 컨테이너 및 이미지 레지스트리

| 항목 | 제약 |
|------|------|
| 컨테이너 런타임 | Docker (빌드 환경) |
| 이미지 레지스트리 | **GitHub Container Registry (GHCR)** 우선, DockerHub 대안 |
| 이미지 명명 규칙 | `ghcr.io/<github-org>/<app-name>:<git-sha>` |
| base 이미지 | 공식 이미지만 사용 (alpine/slim 계열 권장) |
| 멀티스테이지 빌드 | Java 앱 필수, Node/Python 권장 |

---

## 3. CI (지속적 통합)

| 항목 | 제약 |
|------|------|
| CI 플랫폼 | **GitHub Actions** (다른 CI 플랫폼 사용 불가) |
| 트리거 | `main` 브랜치 push, Pull Request |
| 워크플로우 파일 위치 | `.github/workflows/` |
| 시크릿 관리 | GitHub Actions Secrets (`GHCR_TOKEN`, `DOCKERHUB_TOKEN` 등) |
| 타사 Action 사용 | GitHub 공식 Action 및 검증된 Action만 사용 (예: `actions/checkout@v4`, `docker/build-push-action@v5`) |

---

## 4. CD (지속적 배포)

| 항목 | 제약 |
|------|------|
| CD 도구 | **ArgoCD** (다른 CD 도구 사용 불가) |
| GitOps 저장소 | 동일 Git 저장소 (`manifests/` 디렉토리) 또는 별도 config 저장소 |
| 배포 방식 | ArgoCD Application CR 기반 자동 동기화 |
| Helm 사용 | **금지** — plain YAML 매니페스트만 허용 |
| Kustomize | 선택적 허용 (단, 필수 아님) |

---

## 5. K8s 매니페스트

| 항목 | 제약 |
|------|------|
| 매니페스트 형식 | **plain YAML** (Helm Chart 금지) |
| 필수 리소스 | `Deployment`, `Service`, `Ingress` |
| Namespace | 앱별 또는 공통 namespace 사용 (기본: `default` 또는 `apps`) |
| API 버전 | K8s 1.24+ 호환 API 버전 사용 |
| 이미지 태그 | `latest` 고정 금지 — git SHA 태그 사용 |

---

## 6. 로컬 클러스터 (개발/검증)

| 항목 | 제약 |
|------|------|
| 로컬 클러스터 | **Rancher Desktop (k3s)** |
| 운영 체제 | macOS (Apple Silicon / Intel 모두 지원) |
| Ingress Controller | k3s 기본 Traefik 또는 Nginx Ingress Controller |
| ArgoCD 설치 방법 | kubectl apply (공식 install.yaml) |
| 로컬 도메인 | `/etc/hosts` 기반 (`*.local` 도메인) |

---

## 7. 클라우드 (운영 전환 대상)

| 항목 | 제약 |
|------|------|
| 클라우드 제공자 | **AWS** |
| 관리형 K8s | **Amazon EKS** |
| 전환 조건 | 로컬 k3s에서 전체 파이프라인 검증 완료 후 전환 |
| 네트워킹 | AWS Load Balancer Controller 또는 Nginx Ingress + NLB |
| IAM | IRSA(IAM Roles for Service Accounts) 권장 |
| 이미지 레지스트리 | GHCR 유지 (ECR 전환은 선택) |

---

## 8. 전제조건 (프로젝트 시작 전 필요 항목)

| 항목 | 설명 |
|------|------|
| GitHub 계정 | 저장소 소유 및 GitHub Actions 실행 권한 |
| GitHub Personal Access Token | GHCR 이미지 푸시 권한 (`write:packages`) |
| Rancher Desktop 설치 | 로컬 k3s 클러스터 실행 환경 |
| kubectl 설치 | K8s 클러스터 관리 CLI |
| Docker CLI | 로컬 이미지 빌드/푸시 (Rancher Desktop에 포함) |
| AWS CLI | EKS 전환 시 필요 |
| eksctl 또는 Terraform | EKS 클러스터 생성 시 필요 |

---

## 9. 금지 사항

| 항목 | 이유 |
|------|------|
| Helm Chart 사용 | 복잡도 증가, 학습 목적에 부적합 |
| `latest` 태그 고정 배포 | 버전 추적 불가, GitOps 원칙 위반 |
| 하드코딩된 시크릿 | 보안 위반 |
| root 컨테이너 실행 | 보안 위험 |
| 직접 클러스터 배포 (kubectl apply) | GitOps 원칙 위반 — 반드시 ArgoCD를 통한 배포 |
