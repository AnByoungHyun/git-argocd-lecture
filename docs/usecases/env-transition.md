# 유즈케이스: 환경 전환 (로컬 k3s → AWS EKS)

> 문서 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 작성: docs 에이전트  
> 관련 요구사항: [기능 요구사항 §5](../requirements/functional.md), [제약조건 §6,7](../requirements/constraints.md)

---

## 개요

로컬 Rancher Desktop(k3s) 환경에서 전체 CI/CD 파이프라인 검증 완료 후 AWS EKS로 전환하는 절차를 정의한다.  
환경 의존성을 최소화하여 매니페스트 수정을 최소화하는 것이 목표다.

### 전환 원칙

| 원칙 | 설명 |
|------|------|
| **이식성 우선** | 동일한 매니페스트를 양쪽 환경에서 최대한 재사용 |
| **검증 후 전환** | 로컬에서 완전히 검증된 이후에만 EKS 전환 진행 |
| **점진적 전환** | 앱 단위로 순차 전환, 한번에 전체 전환 금지 |
| **GitOps 유지** | EKS 전환 후에도 ArgoCD를 통한 배포만 허용 |

---

## UC-ENV-001: 로컬 k3s 검증 완료 → AWS EKS 전환

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-ENV-001 |
| **이름** | 로컬 k3s에서 AWS EKS로 환경 전환 |
| **액터** | 운영자 (infra 엔지니어) |
| **트리거** | 로컬 k3s 환경에서 전체 파이프라인 검증 완료 확인 |
| **우선순위** | 필수 |

### 전환 전 검증 체크리스트 (로컬 k3s)

다음 항목이 모두 통과된 이후에만 EKS 전환을 진행한다.

| # | 검증 항목 | 확인 방법 |
|---|---------|---------|
| 1 | 3개 앱 모두 빌드/테스트 CI 통과 | GitHub Actions 워크플로우 ✅ |
| 2 | GHCR에 이미지 정상 푸시 | `docker pull ghcr.io/<org>/<app>:<sha>` |
| 3 | ArgoCD가 3개 앱 모두 Synced/Healthy | ArgoCD UI 확인 |
| 4 | `/health` 엔드포인트 응답 정상 | `curl http://<local-domain>/<app>/health` |
| 5 | Ingress 경로 라우팅 정상 | 각 앱 엔드포인트 접근 확인 |
| 6 | 이미지 태그 업데이트 → 자동 배포 (UC-CD-001) | 태그 변경 후 ArgoCD 동기화 확인 |
| 7 | Self-healing 동작 확인 (UC-CD-002) | kubectl 수동 변경 후 복원 확인 |
| 8 | 롤백 동작 확인 (UC-CD-003) | ArgoCD 롤백 또는 git revert 검증 |

### 전환 시 변경 필요 항목

#### 1. Ingress Class

| 항목 | 로컬 k3s (Rancher Desktop) | AWS EKS |
|------|--------------------------|---------|
| Ingress Controller | Traefik (기본) 또는 Nginx | AWS Load Balancer Controller 또는 Nginx Ingress |
| IngressClass | `traefik` 또는 `nginx` | `alb` 또는 `nginx` |
| 어노테이션 | (없음 또는 Traefik 전용) | ALB 전용 어노테이션 필요 |

**변경 예시 (Nginx Ingress 유지 시 최소 변경):**

```yaml
# 로컬 k3s
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: java-app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx   # ← 변경 불필요 (Nginx 유지 시)

# AWS EKS (ALB 사용 시)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: java-app-ingress
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  ingressClassName: alb     # ← 변경 필요
```

#### 2. StorageClass

| 항목 | 로컬 k3s | AWS EKS |
|------|---------|---------|
| 기본 StorageClass | `local-path` | `gp2` 또는 `gp3` (EBS CSI) |
| 영향 범위 | 샘플 앱은 PVC 미사용 → 영향 없음 | — |
| 향후 PVC 필요 시 | StorageClass 변경 필요 | `gp3` 권장 |

> ⚠️ 현재 3개 샘플 앱은 Stateless로 PVC를 사용하지 않으므로 StorageClass 변경 불필요.

#### 3. 로드밸런서 타입

| 항목 | 로컬 k3s | AWS EKS |
|------|---------|---------|
| Service 타입 | `ClusterIP` (Ingress 사용) | `ClusterIP` 유지 (Ingress → ALB/NLB) |
| 외부 노출 방식 | `/etc/hosts` + Traefik NodePort | ALB (인터넷 도메인) |
| 변경 필요 여부 | — | Ingress 어노테이션만 변경 |

#### 4. ArgoCD 재설정

| 항목 | 로컬 k3s | AWS EKS |
|------|---------|---------|
| ArgoCD 설치 위치 | 로컬 k3s 클러스터 | EKS 클러스터 (재설치 또는 외부 ArgoCD) |
| 클러스터 등록 | `in-cluster` (동일 클러스터) | `argocd cluster add <eks-context>` |
| Git 저장소 | 동일 (변경 없음) | 동일 (변경 없음) |
| Application CR | `server: https://kubernetes.default.svc` | EKS API 서버 URL로 변경 |

#### 5. 이미지 레지스트리 인증

| 항목 | 로컬 k3s | AWS EKS |
|------|---------|---------|
| GHCR 인증 | Rancher Desktop 로컬 설정 또는 K8s Secret | K8s imagePullSecret 필수 |
| imagePullSecret | 선택적 | **필수** (`regcred` Secret 생성) |

```bash
# EKS에서 GHCR 인증 Secret 생성
kubectl create secret docker-registry regcred \
  --docker-server=ghcr.io \
  --docker-username=<github-username> \
  --docker-password=<ghcr-token>
```

#### 6. 도메인 및 TLS

| 항목 | 로컬 k3s | AWS EKS |
|------|---------|---------|
| 도메인 | `/etc/hosts` 기반 (`*.local`) | 실제 도메인 또는 ALB DNS |
| TLS | 미적용 (로컬) | ACM 인증서 연동 권장 |
| 변경 항목 | — | Ingress host, TLS 설정 추가 |

---

### 전환 절차 (단계별)

#### Phase 1: EKS 클러스터 준비

```bash
# 1. eksctl로 EKS 클러스터 생성
eksctl create cluster \
  --name argocd-lecture \
  --region ap-northeast-2 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2

# 2. kubeconfig 업데이트
aws eks update-kubeconfig --name argocd-lecture --region ap-northeast-2

# 3. 클러스터 연결 확인
kubectl get nodes
```

#### Phase 2: 필수 컴포넌트 설치

```bash
# 1. ArgoCD 설치
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 2. AWS Load Balancer Controller 설치 (ALB 사용 시)
#    또는 Nginx Ingress Controller 설치 (Nginx 유지 시)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/aws/deploy.yaml

# 3. GHCR imagePullSecret 생성
kubectl create secret docker-registry regcred \
  --docker-server=ghcr.io \
  --docker-username=<github-username> \
  --docker-password=<ghcr-token>
```

#### Phase 3: 매니페스트 수정 (최소 변경)

변경 대상 파일:

| 파일 | 변경 내용 |
|------|---------|
| `manifests/*/ingress.yaml` | `ingressClassName` 및 어노테이션 변경 |
| `manifests/*/deployment.yaml` | `imagePullSecrets` 추가 |
| ArgoCD Application CR | `server` URL EKS로 변경 |

#### Phase 4: ArgoCD Application 등록 및 검증

```bash
# 1. ArgoCD에 EKS 클러스터 등록
argocd cluster add <eks-context-name>

# 2. Application CR 적용
kubectl apply -f argocd/applications/

# 3. 동기화 확인
argocd app sync java-app
argocd app get java-app

# 4. 헬스체크 확인
curl http://<alb-dns>/health
```

#### Phase 5: 검증 및 전환 완료

| # | 검증 항목 |
|---|---------|
| 1 | 3개 앱 ArgoCD Application Synced/Healthy 확인 |
| 2 | 각 앱 `/health` 엔드포인트 응답 확인 |
| 3 | CI push → EKS 자동 배포 동작 확인 |
| 4 | Self-healing 동작 확인 |

---

### 환경별 비교 요약

| 항목 | 로컬 k3s (Rancher Desktop) | AWS EKS | 변경 필요 |
|------|--------------------------|---------|---------|
| K8s 배포 | `Deployment` (동일) | `Deployment` (동일) | ❌ 없음 |
| `Service` | `ClusterIP` (동일) | `ClusterIP` (동일) | ❌ 없음 |
| `Ingress` class | `traefik`/`nginx` | `alb`/`nginx` | ⚠️ 변경 |
| `Ingress` 어노테이션 | Traefik/Nginx 전용 | ALB 전용 (ALB 사용 시) | ⚠️ 변경 |
| `StorageClass` | `local-path` | `gp2`/`gp3` | ❌ 현재 미사용 |
| imagePullSecret | 선택 | 필수 | ⚠️ 추가 |
| TLS | 미적용 | ACM 연동 권장 | ⚠️ 선택적 추가 |
| ArgoCD | 로컬 클러스터 내 | EKS 클러스터 내 | ⚠️ 재설치 |
| CI (GitHub Actions) | 동일 | 동일 | ❌ 없음 |
| 이미지 레지스트리 (GHCR) | 동일 | 동일 | ❌ 없음 |
| Git 저장소 | 동일 | 동일 | ❌ 없음 |

> **핵심**: 매니페스트 변경은 Ingress 설정 및 imagePullSecret 추가에 집중된다.  
> CI 파이프라인, 이미지 레지스트리, Git 저장소는 변경 없이 그대로 재사용한다.
