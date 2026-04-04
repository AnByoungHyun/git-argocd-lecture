# 06. 로컬 클러스터 구성 + ArgoCD 설치

> 가이드 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 상태: ✅ 실습 단계 작성 완료  
> 이전 가이드: [05. K8s 매니페스트](05-k8s-manifests.md) | 다음 가이드: [07. GitOps 배포 실습](07-gitops-deploy.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] Rancher Desktop(k3s)의 클러스터 상태를 확인하고 기본 컴포넌트를 이해할 수 있다
- [ ] Nginx Ingress Controller를 설치하고 외부 IP를 확인할 수 있다
- [ ] ArgoCD를 설치하고 UI에 접근할 수 있다
- [ ] ArgoCD AppProject + Application CR을 적용하고 Synced/Healthy 상태를 확인할 수 있다

---

## 📖 이론

### Rancher Desktop과 k3s

**Rancher Desktop**은 macOS에서 K8s 개발 환경을 제공하는 GUI 도구입니다.  
내부적으로 **k3s** (경량 K8s 배포판)를 가상 머신(Lima VM) 위에서 실행합니다.

```
macOS 호스트
└── Lima VM (경량 Linux 가상 머신)
     └── k3s (경량 Kubernetes 배포판)
          ├── kube-apiserver      ← 모든 API 요청 처리
          ├── kube-scheduler      ← Pod를 어느 노드에 배치할지 결정
          ├── kube-controller-manager ← 클러스터 상태 관리
          ├── SQLite              ← etcd 대신 사용 (경량화 핵심)
          ├── containerd          ← 컨테이너 런타임
          └── kube-proxy          ← 네트워크 규칙 관리
```

---

### 1. Ingress Controller란?

#### 외부 트래픽을 내부 서비스로 연결하는 문지기

Kubernetes 클러스터 안에서 각 앱은 **Service** 라는 내부 주소를 갖고 있습니다.  
하지만 Service는 기본적으로 클러스터 **내부에서만** 통신합니다.  
외부 사용자(브라우저, curl 등)가 앱에 접근하려면 **진입점**이 필요합니다.

바로 그 역할을 하는 것이 **Ingress Controller**입니다.

```
외부 요청 흐름:
                                    Ingress Controller
사용자 브라우저                       (Nginx / Traefik)          K8s 내부
─────────────                    ──────────────────────    ──────────────────
http://apps.local/java    →      경로 매핑 규칙 확인      →  java-app Service
http://apps.local/node    →      /java  → java-app-svc    →  node-app Service
http://apps.local/python  →      /node  → node-app-svc    →  python-app Service
                                 /python → python-app-svc
```

#### Ingress vs Service 타입 비교

| 접근 방법 | 설명 | 이 강의 사용 |
|-----------|------|:-----------:|
| ClusterIP | 클러스터 내부에서만 접근 가능 (기본값) | Service 기본 타입 |
| NodePort | 노드의 특정 포트를 외부에 개방 (30000~32767) | ArgoCD UI |
| LoadBalancer | 외부 로드밸런서 IP를 Service에 할당 | Nginx Ingress |
| **Ingress** | 경로/도메인 기반 라우팅, L7 규칙 적용 | **앱 트래픽 진입** |

#### Traefik vs Nginx Ingress Controller

k3s는 기본으로 **Traefik** Ingress Controller를 포함합니다.  
이 실습에서는 **Nginx**를 사용합니다.

| 항목 | Traefik (k3s 기본) | Nginx Ingress Controller |
|------|-------------------|--------------------------|
| 설치 | k3s 자동 설치 | 별도 `kubectl apply` 필요 |
| 설정 방식 | CRD (IngressRoute) 또는 Ingress | 표준 K8s Ingress + annotation |
| Path rewrite | 별도 미들웨어 CRD 필요 | annotation 한 줄로 설정 |
| AWS EKS 호환성 | 별도 설치 필요 | ✅ 동일 방식으로 사용 가능 |
| 운영 환경 채택률 | 중간 | **매우 높음** (업계 표준) |

#### 이 프로젝트에서 Nginx를 선택한 이유

```
핵심 이유: 로컬 실습 경험을 클라우드 운영 환경에 그대로 적용

1. AWS EKS + Nginx Ingress Controller 조합이 업계 표준
   → 로컬에서 배운 annotation, rewrite 규칙이 EKS에서도 동일하게 동작

2. path rewrite 규칙이 annotation 하나로 처리됨
   nginx.ingress.kubernetes.io/rewrite-target: /$2
   → /java/health → /health 변환 (앱 코드 수정 불필요)

3. 표준 K8s Ingress 리소스 기반
   → IngressClass: nginx 지정만으로 충분
   → 특수 CRD 없이 일반 yaml로 표현 가능
```

---

### 2. ArgoCD 아키텍처

#### 전체 구성도

```
                          GitHub 저장소
                       (manifests/ 경로)
                              │
                    3분 주기 폴링 (Repo Server)
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                   ArgoCD (argocd 네임스페이스)              │
│                                                            │
│  ┌──────────────────┐   ┌────────────────────────────────┐ │
│  │  argocd-server   │   │     argocd-repo-server         │ │
│  │  (API + UI)      │   │  (Git 접근 + 매니페스트 렌더링) │ │
│  │  :8080 / :443    │   └────────────────┬───────────────┘ │
│  └────────┬─────────┘                    │ 렌더링된 매니페스트  │
│           │ kubectl / API                │                  │
│  ┌────────▼──────────────────────────────▼───────────────┐ │
│  │          argocd-application-controller                │ │
│  │    (K8s 상태 감시 + Git 상태와 비교 + 동기화 실행)     │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────┐   ┌───────────────────────────────┐  │
│  │  argocd-redis    │   │  argocd-dex-server            │  │
│  │  (상태 캐시)      │   │  (SSO 인증 — GitHub/LDAP 연동)│  │
│  └──────────────────┘   └───────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
                              │
              K8s API (kubectl apply 등가 작업)
                              │
                    K8s 클러스터 (apps 네임스페이스)
                 Deployment / Service / Ingress 관리
```

#### 각 컴포넌트의 역할

| 컴포넌트 | 역할 | 핵심 동작 |
|----------|------|-----------|
| **argocd-server** | UI/API 서버 | 브라우저 UI, ArgoCD CLI, 외부 시스템 연동 API 제공 |
| **argocd-repo-server** | Git 접근 전담 | 저장소 Clone/Fetch, YAML/Helm/Kustomize 렌더링 |
| **argocd-application-controller** | 핵심 조정 루프 | Git 상태 vs K8s 상태 비교 → 동기화 결정 및 실행 |
| **argocd-redis** | 캐시 레이어 | 클러스터 상태·렌더링 결과 캐싱 (성능 최적화) |
| **argocd-dex-server** | SSO 인증 | GitHub/GitLab/LDAP 연동 로그인 (선택 사항) |
| **argocd-applicationset-controller** | 앱 자동 생성 | ApplicationSet CRD로 다수 앱을 패턴으로 정의 |
| **argocd-notifications-controller** | 알림 발송 | Slack/Email 등 배포 이벤트 알림 |

#### ArgoCD가 Git 저장소를 감시하는 원리 (Polling)

ArgoCD는 Push 방식이 아닌 **Pull(폴링) 방식**으로 Git을 감시합니다.

```
시간 흐름:
  t=0분   argocd-repo-server가 GitHub에 접속
          → manifests/java-app/deployment.yaml SHA 확인
          → 클러스터 현재 Deployment와 비교
          → 동일함 → "Synced" 상태 유지

  t=3분   (기본 3분 주기) 다시 폴링
          → deployment.yaml 변경 감지! (이미지 태그 업데이트)
          → "OutOfSync" 상태로 전환
          → auto-sync 설정 시 → argocd-application-controller가
            kubectl apply 동등 작업 즉시 실행
          → "Synced" + "Progressing" → "Synced" + "Healthy"
```

> 💡 **폴링 주기를 줄이고 싶다면?**  
> GitHub Webhook을 설정하면 push 즉시 ArgoCD에 알림이 전달되어  
> 3분을 기다리지 않아도 됩니다. 이 강의에서는 기본 폴링 방식을 사용합니다.

---

### 3. ArgoCD Application CR 구조

ArgoCD에서 "어떤 Git 저장소의 어떤 경로를 어떤 클러스터에 배포할지"를  
정의하는 것이 **Application CR(Custom Resource)**입니다.

```yaml
# argocd/applications/java-app.yaml 전체 설명
apiVersion: argoproj.io/v1alpha1
kind: Application          # ArgoCD가 정의한 Custom Resource
metadata:
  name: java-app           # ArgoCD UI에서 보이는 앱 이름
  namespace: argocd        # Application CR 자체는 argocd 네임스페이스에 위치
  finalizers:
    - resources-finalizer.argocd.argoproj.io  # 앱 삭제 시 K8s 리소스도 함께 정리
spec:
  project: cicd-project    # 소속 AppProject (접근 권한 범위 정의)

  # ─── Source: 어디서 가져올 것인가 ────────────────────────────
  source:
    repoURL: https://github.com/AnByoungHyun/git-argocd-lecture.git
    #         ↑ Git 저장소 URL (ArgoCD가 이 주소를 폴링)

    targetRevision: main
    #               ↑ 감시할 브랜치/태그/커밋 SHA
    #                 "main" = 항상 최신 main 브랜치 추적

    path: manifests/java-app
    #     ↑ 저장소 내 매니페스트가 있는 경로
    #       이 디렉토리의 모든 .yaml 파일을 적용

  # ─── Destination: 어디에 배포할 것인가 ─────────────────────
  destination:
    server: https://kubernetes.default.svc
    #       ↑ 배포 대상 K8s 클러스터 API 주소
    #         "kubernetes.default.svc" = ArgoCD가 실행 중인 클러스터 자신

    namespace: apps
    #          ↑ 배포될 K8s 네임스페이스

  # ─── SyncPolicy: 언제, 어떻게 동기화할 것인가 ──────────────
  syncPolicy:
    automated:             # 자동 동기화 활성화
      prune: true          # Git에서 파일 삭제 시 클러스터 리소스도 삭제
      selfHeal: true       # kubectl로 직접 변경 시 Git 상태로 자동 복원
    syncOptions:
      - CreateNamespace=true         # apps 네임스페이스 없으면 자동 생성
      - PrunePropagationPolicy=foreground  # 리소스 삭제 순서 보장
      - PruneLast=true               # 삭제는 생성/업데이트 후 마지막에 실행
    retry:                 # 동기화 실패 시 재시도 정책
      limit: 3             # 최대 3회 재시도
      backoff:
        duration: 5s       # 첫 재시도 대기 시간
        factor: 2          # 지수 백오프 (5s → 10s → 20s)
        maxDuration: 3m    # 최대 대기 시간
```

#### syncPolicy 동작 시나리오

```
prune: true 시나리오:
  Git에서 manifests/java-app/configmap.yaml 파일 삭제
    └→ ArgoCD 감지: "K8s에 ConfigMap이 있는데 Git에는 없다 = OutOfSync"
    └→ prune: true → K8s에서 ConfigMap 자동 삭제
    └→ prune: false(기본) → 경고만 표시, 삭제 안 함

selfHeal: true 시나리오:
  운영자가 실수로 kubectl로 replicas를 5로 변경
    └→ ArgoCD 감지: "Git은 replicas:2 인데 클러스터는 5 = OutOfSync"
    └→ selfHeal: true → 즉시 replicas:2로 자동 복원
    └→ selfHeal: false(기본) → OutOfSync 경고만 표시
```

---

### 4. AppProject의 역할

#### AppProject = ArgoCD 내의 권한 경계

**AppProject**는 ArgoCD에서 여러 팀/환경을 격리할 때 사용하는 권한 단위입니다.  
"어떤 Git 저장소에서, 어떤 클러스터에, 어떤 네임스페이스로 배포할 수 있는가"를 정의합니다.

```yaml
# argocd/applications/project.yaml 설명
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: cicd-project       # Application CR의 spec.project 값과 일치해야 함
  namespace: argocd
spec:
  description: "GitOps CI/CD 강의 프로젝트"

  # ─── 허용된 Git 소스 저장소 ──────────────────────────────────
  sourceRepos:
    - https://github.com/AnByoungHyun/git-argocd-lecture.git
    # 이 저장소에서만 매니페스트를 가져올 수 있음
    # "*" 로 모든 저장소 허용 가능 (보안 취약 — 운영 환경에서 비권장)

  # ─── 배포 허용 대상 ──────────────────────────────────────────
  destinations:
    - server: https://kubernetes.default.svc
      namespace: apps        # apps 네임스페이스만 배포 허용
    - server: https://kubernetes.default.svc
      namespace: argocd      # argocd 네임스페이스도 허용 (AppProject 자체 배포용)

  # ─── 클러스터 레벨 리소스 허용 ───────────────────────────────
  clusterResourceWhitelist:
    - group: ""
      kind: Namespace        # Namespace 생성 권한 (CreateNamespace=true 사용 시 필요)

  # ─── 네임스페이스 레벨 리소스 허용 ──────────────────────────
  namespaceResourceWhitelist:
    - group: "apps"
      kind: Deployment       # Deployment 생성/수정 허용
    - group: ""
      kind: Service          # Service 허용
    - group: "networking.k8s.io"
      kind: Ingress          # Ingress 허용
```

#### 멀티 팀 환경에서의 격리 예시

```
실제 운영 환경 예시:

AppProject: backend-team
  sourceRepos: github.com/company/backend-services
  destinations: cluster-prod → namespace: backend
  → 백엔드 팀은 backend 네임스페이스만 배포 가능

AppProject: frontend-team
  sourceRepos: github.com/company/frontend-services
  destinations: cluster-prod → namespace: frontend
  → 프론트엔드 팀은 frontend 네임스페이스만 배포 가능

효과:
  ✅ 백엔드 팀이 실수로 frontend 네임스페이스에 배포하는 사고 방지
  ✅ 팀별로 다른 Git 저장소만 신뢰 (외부 저장소 주입 공격 방지)
  ✅ RBAC과 결합하여 배포 권한 세밀하게 제어
```

> 💡 **이 강의에서는**: 단일 `cicd-project`를 사용하여  
> 3개 앱 모두 같은 Project 아래 관리합니다.  
> 팀이 많아지면 AppProject를 팀/환경별로 나누는 것이 Best Practice입니다.

---

### 5. ArgoCD UI 접근 방법

ArgoCD UI는 기본적으로 클러스터 내부에서만 접근 가능합니다.  
외부(로컬 브라우저)에서 접근하려면 세 가지 방법이 있습니다.

#### 방법 비교

| 방법 | 설명 | 장점 | 단점 | 적합한 환경 |
|------|------|------|------|------------|
| **Port-forward** | `kubectl port-forward`로 로컬 포트에 터널 | 설정 간단, 추가 리소스 없음 | 터미널 유지 필요, 세션 끊기면 재실행 | 로컬 개발 ✅ |
| **NodePort** | Service를 NodePort 타입으로 변경 | 영구적, 포트 고정 | 포트 범위 제한(30000~32767), TLS 직접 관리 | 로컬 개발, 테스트 ✅ |
| **Ingress** | Ingress 규칙으로 도메인 기반 접근 | 도메인 사용, L7 라우팅 | 별도 설정 필요 | 팀 공유 환경, 운영 ✅✅ |

#### 방법 A: Port-forward (로컬 개발 권장)

```bash
# argocd-server의 443 포트를 로컬 8443으로 포워딩
kubectl port-forward svc/argocd-server -n argocd 8443:443

# 별도 터미널에서 접근
open https://localhost:8443
```

```
동작 원리:
  로컬 PC :8443  ─────────────→  argocd-server Pod :443
              kubectl port-forward 터널
```

**port-forward가 로컬 개발에서 선호되는 이유:**
- `kubectl` 외 추가 설치 불필요
- 접근 권한이 kubeconfig에 의해 자동 제어 (보안)
- 세션 종료 시 자동으로 접근 차단 (실수 방지)
- 테스트 후 깔끔하게 종료 가능

> ⚠️ **단점**: 터미널 세션 유지 필요, 브라우저 새로고침 시 `Ctrl+C`로 끊기면 재접속 불가.  
> 백그라운드 실행 시: `kubectl port-forward svc/argocd-server -n argocd 8443:443 &`

#### 방법 B: NodePort (이 실습 채택)

```bash
# ClusterIP → NodePort 타입 변경
kubectl patch svc argocd-server -n argocd   -p '{"spec":{"type":"NodePort"}}'

# 할당된 노드 포트 확인
kubectl get svc argocd-server -n argocd

# 접근 URL: https://<노드 IP>:<NodePort>
# Rancher Desktop: https://192.168.64.2:<할당된 포트>
```

**NodePort를 이 실습에서 사용하는 이유:**
- 터미널 없이도 브라우저로 영구 접근 가능
- 실습 중 여러 번 ArgoCD UI를 열고 닫기 편리
- Rancher Desktop의 노드 IP(192.168.64.2)로 직접 접근

#### 방법 C: Ingress (운영/팀 공유 환경)

```yaml
# ArgoCD 전용 Ingress (참고용 — 이 실습에서는 미사용)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: argocd-ingress
  namespace: argocd
  annotations:
    nginx.ingress.kubernetes.io/ssl-passthrough: "true"
    nginx.ingress.kubernetes.io/backend-protocol: "HTTPS"
spec:
  ingressClassName: nginx
  rules:
    - host: argocd.apps.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: argocd-server
                port:
                  number: 443
```

#### 보안 고려사항

```
초기 설치 후 반드시 해야 할 보안 조치:

1. 초기 admin 비밀번호 변경
   argocd-initial-admin-secret은 임시 비밀번호입니다.
   → ArgoCD UI: User Info → Update Password
   → 또는 CLI: argocd account update-password

2. HTTPS 사용
   ArgoCD는 기본으로 자체 서명(Self-signed) 인증서를 사용합니다.
   → 로컬 개발: 브라우저 경고 무시 (허용)
   → 운영 환경: Let's Encrypt / ACM 인증서로 교체 필요

3. 사용하지 않을 때 초기 비밀번호 Secret 삭제
   kubectl delete secret argocd-initial-admin-secret -n argocd
   → 비밀번호는 변경 후 이 Secret에서 삭제 (argocd 내부 저장소에 유지됨)

4. 운영 환경에서의 접근 제한
   → VPN 연동 또는 IP 화이트리스트 + Ingress로 접근 제한
   → AppProject RBAC으로 팀별 배포 권한 세밀화
```

---

## 🛠️ 실습 단계

> ⚠️ **사전 조건**: Rancher Desktop 실행 중, `kubectl get nodes` — Ready 상태

---

### Step 1: Rancher Desktop 클러스터 상태 확인

```bash
# 현재 컨텍스트 확인
kubectl config current-context

# 노드 상태 확인
kubectl get nodes -o wide

# 전체 시스템 파드 확인
kubectl get pods -A
```

```
예상 출력:
rancher-desktop  (또는 lima-rancher-desktop)

NAME                   STATUS   ROLES                  AGE    VERSION       INTERNAL-IP
lima-rancher-desktop   Ready    control-plane,master   xx m   v1.28.x       192.168.64.2

NAMESPACE     NAME                                      READY   STATUS    
kube-system   coredns-xxx                              1/1     Running   
kube-system   local-path-provisioner-xxx               1/1     Running   
kube-system   metrics-server-xxx                       1/1     Running   
kube-system   traefik-xxx                              1/1     Running   ← 기본 Traefik
```

✅ **확인**: 노드 `Ready` 상태, kube-system 파드들 `Running`

---

### Step 2: Traefik 제거 (Nginx와 충돌 방지)

k3s 기본 Traefik을 제거하고 Nginx Ingress Controller로 교체합니다.

**방법 A: Rancher Desktop UI에서 비활성화 (권장)**

```
Rancher Desktop 앱 → Preferences (톱니바퀴) → Kubernetes
→ "Enable Traefik" 체크박스 해제 → Apply
→ Kubernetes 재시작 대기 (약 1~2분)
```

**방법 B: kubectl로 직접 제거**

```bash
# k3s HelmChart로 설치된 Traefik 제거
kubectl delete helmchart traefik traefik-crd -n kube-system 2>/dev/null || true

# Traefik 관련 리소스 정리
kubectl delete deployment traefik -n kube-system 2>/dev/null || true

# 제거 확인 (Traefik 파드가 없어야 함)
kubectl get pods -n kube-system | grep traefik
```

```
예상 출력:
(아무것도 출력되지 않음 — Traefik 제거 완료)
```

✅ **확인**: `kubectl get pods -n kube-system | grep traefik` 결과 없음

---

### Step 3: Nginx Ingress Controller 설치

```bash
# Nginx Ingress Controller 설치 (cloud 프로바이더용 — LoadBalancer 타입)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml

# 설치 대기 (파드 Ready까지 약 1~2분)
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

```
예상 출력:
namespace/ingress-nginx created
serviceaccount/ingress-nginx created
...
pod/ingress-nginx-controller-xxx condition met
```

```bash
# External IP 확인
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

```
예상 출력:
NAME                       TYPE           CLUSTER-IP     EXTERNAL-IP     PORT(S)
ingress-nginx-controller   LoadBalancer   10.43.xx.xx    192.168.64.2    80:xxxxx/TCP,443:xxxxx/TCP
```

> 💡 **EXTERNAL-IP가 `<pending>`으로 표시되는 경우**:  
> k3s ServiceLB가 IP를 할당하는 데 시간이 걸릴 수 있습니다. 1~2분 기다린 후 재확인하세요.  
> 또는 노드 IP(`kubectl get nodes -o wide`의 INTERNAL-IP)를 사용하세요.

```bash
# Ingress External IP 저장 (이후 단계에서 사용)
INGRESS_IP=$(kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Ingress IP: $INGRESS_IP"
```

✅ **확인**: `EXTERNAL-IP`에 실제 IP 주소(예: `192.168.64.2`) 확인

---

### Step 4: /etc/hosts 도메인 등록

```bash
# /etc/hosts 현재 상태 확인
grep apps.local /etc/hosts || echo "apps.local 미등록"

# 등록 (sudo 필요)
echo "$INGRESS_IP apps.local" | sudo tee -a /etc/hosts

# 등록 확인
grep apps.local /etc/hosts
```

```
예상 출력:
192.168.64.2 apps.local
```

```bash
# 도메인 해석 테스트
ping -c 1 apps.local
```

```
예상 출력:
PING apps.local (192.168.64.2): 56 data bytes
64 bytes from 192.168.64.2: ...
```

✅ **확인**: `apps.local`이 Ingress IP로 해석됨

---

### Step 5: apps 네임스페이스 생성

```bash
kubectl create namespace apps
kubectl get namespace apps
```

```
예상 출력:
namespace/apps created

NAME   STATUS   AGE
apps   Active   x seconds
```

✅ **확인**: `apps` 네임스페이스 Active

---

### Step 6: ArgoCD 설치

```bash
# argocd 네임스페이스 생성
kubectl create namespace argocd

# ArgoCD 설치 (안정 버전)
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 파드 Ready 대기 (약 2~3분)
echo "ArgoCD 파드 기동 대기 중..."
kubectl wait --namespace argocd \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=argocd-server \
  --timeout=300s
```

```
예상 출력:
namespace/argocd created
customresourcedefinition.apiextensions.k8s.io/applications.argoproj.io created
...
pod/argocd-server-xxx condition met
```

```bash
# 전체 파드 상태 확인 (7개 파드 Running 확인)
kubectl get pods -n argocd
```

```
예상 출력:
NAME                                                READY   STATUS    
argocd-application-controller-xxx                  1/1     Running   
argocd-applicationset-controller-xxx               1/1     Running   
argocd-dex-server-xxx                              1/1     Running   
argocd-notifications-controller-xxx                1/1     Running   
argocd-redis-xxx                                   1/1     Running   
argocd-repo-server-xxx                             1/1     Running   
argocd-server-xxx                                  1/1     Running   ← UI/API 서버
```

✅ **확인**: 7개 파드 모두 `1/1 Running` 상태

---

### Step 7: ArgoCD UI 접근

**방법 A: Port-forward (간단, 권장)**

```bash
# 백그라운드에서 포트포워딩 시작
kubectl port-forward svc/argocd-server -n argocd 8443:443 &
PF_PID=$!
echo "Port-forward PID: $PF_PID"

# 브라우저에서 접근
open https://localhost:8443
```

**방법 B: NodePort로 외부 접근**

```bash
# argocd-server를 NodePort로 변경
kubectl patch svc argocd-server -n argocd \
  -p '{"spec":{"type":"NodePort"}}'

# 할당된 포트 확인
kubectl get svc argocd-server -n argocd
```

```
예상 출력:
NAME            TYPE       CLUSTER-IP    EXTERNAL-IP   PORT(S)                      
argocd-server   NodePort   10.43.xx.xx   <none>        80:3xxxx/TCP,443:3xxxx/TCP
                                                              ↑ 이 포트 사용
```

```bash
# NodePort 번호 확인 (443 → NodePort)
ARGOCD_PORT=$(kubectl get svc argocd-server -n argocd \
  -o jsonpath='{.spec.ports[?(@.port==443)].nodePort}')
echo "ArgoCD URL: https://$INGRESS_IP:$ARGOCD_PORT"
open "https://$INGRESS_IP:$ARGOCD_PORT"
```

**초기 admin 비밀번호 확인**

```bash
# 초기 비밀번호 조회 (base64 디코딩)
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo ""
```

```
예상 출력:
XxXxXxXxXxXxXxXx   ← 이 값이 admin 초기 비밀번호
```

```
브라우저 로그인:
  URL: https://localhost:8443 (또는 NodePort URL)
  Username: admin
  Password: (위에서 확인한 값)

  ⚠️ 브라우저 보안 경고 → "고급" → "안전하지 않음으로 계속" 클릭
  (자체 서명 인증서 사용 — 로컬 환경에서 정상)
```

✅ **확인**: ArgoCD UI 로그인 성공, `Applications` 목록 페이지 표시

---

### Step 8: GHCR imagePullSecret 생성

```bash
# regcred Secret 생성 (deployment.yaml의 imagePullSecrets에 명시된 이름)
kubectl create secret docker-registry regcred \
  --docker-server=ghcr.io \
  --docker-username=$GITHUB_USERNAME \
  --docker-password=$GITHUB_TOKEN \
  --namespace=apps \
  --dry-run=client -o yaml | kubectl apply -f -

# 생성 확인
kubectl get secret regcred -n apps -o yaml | grep type
```

```
예상 출력:
secret/regcred configured (또는 created)

type: kubernetes.io/dockerconfigjson
```

> 💡 **GHCR 이미지를 Public으로 공개한 경우** 이 단계를 건너뛸 수 있습니다.

✅ **확인**: `regcred` Secret 존재 확인

---

### Step 9: ArgoCD AppProject + Application CR 적용

```bash
cd ~/workspace/git-argocd-lecture

# ⚠️ 중요: argocd/ 파일의 repoURL을 본인 저장소로 수정해야 합니다
# 원본: https://github.com/AnByoungHyun/git-argocd-lecture.git
# 수정: https://github.com/<your-username>/git-argocd-lecture.git

# 현재 설정 확인
grep "repoURL" argocd/applications/project.yaml
grep "repoURL" argocd/applications/java-app.yaml
```

```bash
# repoURL을 본인 저장소로 수정 (username 변경)
sed -i '' "s|AnByoungHyun|$GITHUB_USERNAME|g" argocd/applications/project.yaml
sed -i '' "s|AnByoungHyun|$GITHUB_USERNAME|g" argocd/applications/java-app.yaml
sed -i '' "s|AnByoungHyun|$GITHUB_USERNAME|g" argocd/applications/node-app.yaml
sed -i '' "s|AnByoungHyun|$GITHUB_USERNAME|g" argocd/applications/python-app.yaml

# 수정 확인
grep "repoURL" argocd/applications/java-app.yaml
```

```
예상 출력:
    repoURL: https://github.com/<your-username>/git-argocd-lecture.git
```

```bash
# AppProject 먼저 적용 (Application보다 먼저)
kubectl apply -f argocd/applications/project.yaml

# 3개 Application CR 적용
kubectl apply -f argocd/applications/java-app.yaml
kubectl apply -f argocd/applications/node-app.yaml
kubectl apply -f argocd/applications/python-app.yaml

# 적용 결과 확인
kubectl get applications -n argocd
```

```
예상 출력:
NAME         SYNC STATUS   HEALTH STATUS
java-app     Synced        Healthy
node-app     Synced        Healthy
python-app   Synced        Healthy
```

> ⏳ 처음에는 `OutOfSync` → `Syncing` → `Synced` 순서로 전환됩니다. 1~3분 소요.

✅ **확인**: 3개 Application 모두 `Synced` + `Healthy`

---

### Step 10: ArgoCD UI에서 앱 상태 확인

브라우저에서 ArgoCD UI를 열고 각 앱 상태를 확인합니다.

```
ArgoCD UI 확인 항목:

Applications 목록:
  java-app    ● Synced  ✅ Healthy
  node-app    ● Synced  ✅ Healthy
  python-app  ● Synced  ✅ Healthy

각 앱 클릭 시 확인:
  - 리소스 트리: Deployment → ReplicaSet → Pod → Service → Ingress
  - Sync History: 최근 동기화 시각
  - App Details: Git 저장소 URL, 대상 브랜치, 경로
```

```bash
# CLI로도 확인 가능
kubectl get applications -n argocd -o wide
```

```
예상 출력:
NAME         SYNC STATUS   HEALTH STATUS   REVISION
java-app     Synced        Healthy         main
node-app     Synced        Healthy         main
python-app   Synced        Healthy         main
```

✅ **확인**: 브라우저 UI에서 3개 앱 `Synced` + `Healthy` 시각적 확인

---

### Step 11: 최종 동작 검증

```bash
# 전체 파드 상태
kubectl get pods -n apps

# Ingress를 통한 접근 테스트
curl -s http://apps.local/java/health | python3 -m json.tool
curl -s http://apps.local/node/health | python3 -m json.tool
curl -s http://apps.local/python/health | python3 -m json.tool
```

```
예상 출력 (각 앱):
{"status": "ok", "app": "java-app", "version": "..."}
{"status": "ok", "app": "node-app", "version": "..."}
{"status": "ok", "app": "python-app", "version": "..."}
```

✅ **확인**: 3개 앱 모두 `apps.local` 도메인을 통해 정상 응답

---

## ✅ 확인 체크리스트

- [ ] `kubectl get nodes` — 노드 `Ready` 상태
- [ ] Traefik 제거 완료 (`kubectl get pods -n kube-system | grep traefik` — 결과 없음)
- [ ] Nginx Ingress Controller External IP 확인 (`192.168.64.2` 또는 실제 IP)
- [ ] `/etc/hosts`에 `apps.local` 등록, `ping apps.local` 성공
- [ ] `kubectl get pods -n argocd` — 7개 파드 모두 `Running`
- [ ] ArgoCD UI 로그인 성공 (admin 계정)
- [ ] `regcred` imagePullSecret 생성 완료
- [ ] ArgoCD Application CR 수정 완료 (repoURL → 본인 저장소)
- [ ] `kubectl get applications -n argocd` — 3개 앱 `Synced` + `Healthy`
- [ ] `curl http://apps.local/java/health` — `"status": "ok"` 응답 확인

---

**다음 단계**: [07. GitOps 배포 실습 — 전체 파이프라인 연동](07-gitops-deploy.md)
