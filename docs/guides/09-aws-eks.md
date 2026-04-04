# 09. (심화) AWS EKS 전환

> 가이드 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 상태: ✅ 실습 단계 작성 완료  
> 이전 가이드: [07. GitOps 배포 실습](07-gitops-deploy.md)  
> 참고 문서: [환경 전환 유즈케이스](../usecases/env-transition.md) | [제약조건 §7](../requirements/constraints.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] 로컬 k3s 환경과 AWS EKS 환경의 차이점을 설명할 수 있다
- [ ] EKS 클러스터를 생성하고 ArgoCD를 재설치할 수 있다
- [ ] 최소한의 매니페스트 수정으로 EKS에 3개 앱을 배포할 수 있다
- [ ] 동일한 CI/CD 파이프라인이 EKS 환경에서도 동작하는 것을 확인할 수 있다
- [ ] 실습 종료 후 AWS 리소스를 완전히 정리하여 불필요한 비용을 방지할 수 있다

---

## 📖 이론

---

### 1. Amazon EKS란?

#### 관리형 Kubernetes 서비스의 의미

Kubernetes를 직접 설치하면 **마스터 노드(Control Plane)**를 직접 구성하고 유지해야 합니다.  
etcd 백업, API 서버 고가용성, 버전 업그레이드 — 이 모든 작업을 운영팀이 담당합니다.

**Amazon EKS(Elastic Kubernetes Service)**는 Control Plane을 AWS가 대신 관리해주는 서비스입니다.

```
직접 구축 K8s:
  [운영팀] → 마스터 노드 설치/패치/백업 + 워커 노드 관리 + 네트워킹 구성
  → 높은 운영 부담, K8s 전문가 필요

Amazon EKS:
  [AWS]    → Control Plane 가용성 99.95% SLA 보장, 버전 업그레이드 지원
  [운영팀] → 워커 노드(EC2) 관리 + 앱 배포만 집중
  → 운영 부담 대폭 감소
```

#### EKS vs 자체 K8s vs 로컬 k3s 비교

| 항목 | 로컬 k3s (Rancher) | 자체 구축 K8s | AWS EKS |
|------|-------------------|-------------|---------|
| **목적** | 로컬 개발/학습 | 온프레미스 운영 | 클라우드 운영 |
| **Control Plane 관리** | 자동 (Rancher) | 직접 관리 ⚠️ | AWS 관리 ✅ |
| **고가용성** | ❌ 단일 노드 | 수동 구성 필요 | 기본 제공 ✅ |
| **비용** | 무료 | 서버 비용 | 시간당 $0.10 + EC2 |
| **스케일링** | ❌ 불가 | 수동 | Auto Scaling ✅ |
| **K8s API 호환성** | ✅ 표준 API | ✅ 표준 API | ✅ 표준 API |
| **사용 시나리오** | 개발/실습 | 대규모 온프레미스 | 프로덕션 권장 |

> 💡 **핵심 메시지**: k3s와 EKS는 모두 동일한 **Kubernetes API**를 사용합니다.  
> 그래서 로컬에서 만든 `Deployment`, `Service`, `Ingress` 매니페스트를  
> EKS에서도 그대로 사용할 수 있습니다.

#### EKS가 관리하는 것 vs 사용자가 관리하는 것

```
┌──────────────────────────────────────────────────────────┐
│                    AWS EKS 책임 모델                       │
│                                                          │
│  ┌─────────────── AWS 관리 영역 ───────────────────┐     │
│  │  EKS Control Plane                              │     │
│  │  ├── kube-apiserver  (고가용성 멀티 AZ)         │     │
│  │  ├── etcd            (자동 백업)                │     │
│  │  ├── kube-scheduler                            │     │
│  │  └── kube-controller-manager                   │     │
│  │                                                │     │
│  │  → 장애 시 AWS가 자동 복구                      │     │
│  │  → 버전 업그레이드 지원                         │     │
│  └────────────────────────────────────────────────┘     │
│                                                          │
│  ┌─────────────── 사용자 관리 영역 ───────────────────┐   │
│  │  Worker Node Group (EC2)                        │   │
│  │  ├── kubelet          (노드 에이전트)             │   │
│  │  ├── kube-proxy       (네트워크 규칙)             │   │
│  │  ├── containerd       (컨테이너 런타임)           │   │
│  │  └── 실제 Pod들 (java-app, node-app, python-app) │   │
│  │                                                 │   │
│  │  → EC2 인스턴스 타입/수량 결정                   │   │
│  │  → 노드 보안 패치 책임                           │   │
│  └─────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

---

### 2. 로컬 k3s → EKS 전환 시 변경되는 것과 유지되는 것

#### 전환 전후 비교 전체 표

| 항목 | 로컬 k3s | AWS EKS | 변경 여부 |
|------|---------|---------|----------|
| **Deployment** | 동일 YAML | 동일 YAML | ❌ 변경 없음 |
| **Service (ClusterIP)** | 동일 | 동일 | ❌ 변경 없음 |
| **Ingress class** | `nginx` | `nginx` | ❌ 변경 없음 |
| **Ingress host** | `apps.local` | NLB DNS 이름 (또는 제거) | ⚠️ 수정 |
| **외부 노출** | `/etc/hosts` 등록 | AWS NLB 자동 프로비저닝 | ⚠️ 자동 처리 |
| **imagePullSecret** | `regcred` | `regcred` (EKS에 재생성) | ⚠️ 재생성 필요 |
| **StorageClass** | `local-path` | `gp2` / `gp3` | △ (현재 미사용) |
| **CI (GitHub Actions)** | 동일 워크플로우 | 동일 워크플로우 | ❌ 변경 없음 |
| **이미지 레지스트리 (GHCR)** | `ghcr.io/...` | `ghcr.io/...` | ❌ 변경 없음 |
| **ArgoCD** | k3s에 설치 | EKS에 재설치 | ⚠️ 재설치 |
| **ArgoCD Application CR** | 동일 YAML | 동일 YAML | ❌ 변경 없음 |
| **GitOps 원칙** | Git → ArgoCD | Git → ArgoCD | ❌ 변경 없음 |

#### 변경이 필요한 3가지

**① Ingress host 수정**

```yaml
# 로컬 k3s — /etc/hosts에 apps.local 등록 필요
spec:
  rules:
    - host: apps.local          # ← 로컬 전용 도메인

# EKS — NLB DNS 이름 사용 (또는 host 제거)
spec:
  rules:
    - host: ""                  # ← host 없이 NLB DNS로 직접 접근
```

**② imagePullSecret 재생성**

로컬의 `regcred` 시크릿은 로컬 클러스터에만 존재합니다.  
EKS는 별도의 클러스터이므로, `apps` 네임스페이스에 시크릿을 다시 생성해야 합니다.

```bash
# EKS 클러스터에서 동일한 명령으로 재생성
kubectl create secret docker-registry regcred   --docker-server=ghcr.io   --docker-username=<GitHub_ID>   --docker-password=<GHCR_PAT>   -n apps
```

**③ ArgoCD 재설치**

ArgoCD는 클러스터 내부에 설치되므로, EKS 클러스터에 새로 설치합니다.  
단, ArgoCD **Application CR YAML은 동일하게 재사용**합니다.

#### 변경 없이 유지되는 것 (이식성이 높은 이유)

```
유지되는 것:
  ✅ CI 파이프라인 (GitHub Actions) — 클러스터와 무관, GHCR에 이미지 푸시
  ✅ 컨테이너 이미지 (GHCR)        — 클라우드 레지스트리, 어디서든 Pull 가능
  ✅ Deployment/Service YAML       — K8s 표준 API, 어느 클러스터든 동작
  ✅ GitOps 방식 (Git → ArgoCD)    — 배포 방식 자체는 동일
  ✅ ArgoCD Application CR         — 동일한 YAML로 재적용
```

**K8s 이식성이 높은 이유: "API 표준화"**

```
Kubernetes는 CNCF(Cloud Native Computing Foundation)가 관리하는
오픈소스 표준입니다.

k3s, EKS, GKE, AKS 모두 동일한 Kubernetes API를 구현합니다:
  kubectl apply -f deployment.yaml
  → k3s에서도 동작
  → EKS에서도 동작
  → GKE(Google)에서도 동작
  → AKS(Azure)에서도 동작

벤더 종속성(Vendor Lock-in) 없이 클라우드 이동이 가능한 핵심 이유입니다.
```

---

### 3. AWS 네트워킹 기초

AWS EKS를 처음 접할 때 가장 낯선 부분이 네트워킹입니다.  
이 프로젝트에서 사용하는 3가지 개념만 이해하면 됩니다.

#### VPC (Virtual Private Cloud) — 나만의 가상 네트워크

```
VPC = AWS 클라우드 안에 만드는 나만의 격리된 네트워크 공간

일반 인터넷       AWS Cloud
   [ 외부 ]  →  [ VPC: 10.0.0.0/16 ] → [ EC2, RDS, EKS ... ]
                  └ 외부에서 직접 접근 불가 (보안 경계)

이 프로젝트의 VPC 구성 (eksctl 자동 생성):
  VPC CIDR: 192.168.0.0/16
  ├── Public Subnet (ap-northeast-2a)  — NLB, NAT Gateway 위치
  ├── Public Subnet (ap-northeast-2b)  — 가용영역 분산
  ├── Private Subnet (ap-northeast-2a) — Worker Node(EC2) 위치
  └── Private Subnet (ap-northeast-2b) — Worker Node 고가용성
```

#### Subnet — VPC 안의 구역 분리

| 서브넷 종류 | 인터넷 접근 | 용도 |
|-----------|-----------|------|
| **Public Subnet** | 가능 (IGW 경유) | NLB, Bastion Host |
| **Private Subnet** | 불가 (NAT 경유만) | Worker Node (EC2), Pod |

> 💡 Worker Node가 Private Subnet에 있는 이유:  
> EC2 인스턴스를 인터넷에 직접 노출시키지 않아 보안성을 높입니다.  
> 외부 트래픽은 Public Subnet의 NLB → Private Subnet의 Nginx Pod 순으로 전달됩니다.

#### Security Group — EC2의 방화벽

```
Security Group = 인바운드/아웃바운드 트래픽 필터 (EC2 단위)

Worker Node Security Group 예시:
  인바운드:
    - TCP 443  from EKS Control Plane  (kubectl API 통신)
    - TCP 80   from NLB               (Nginx Ingress로 전달)
    - All      from 같은 VPC          (Pod 간 통신)
  아웃바운드:
    - All      허용                   (인터넷 접근, ECR Pull 등)
```

#### NLB (Network Load Balancer) — 외부 트래픽 진입점

```
인터넷 사용자
     │
     ↓ (DNS: xxx.elb.ap-northeast-2.amazonaws.com)
[ NLB ] ← AWS가 자동 생성 (Nginx Ingress Service type=LoadBalancer 설정 시)
     │
     ↓ TCP 80/443 전달 (L4 계층)
[ Nginx Ingress Controller Pod ]
     │
     ├── /java   → java-app Service → java-app Pod
     ├── /node   → node-app Service → node-app Pod
     └── /python → python-app Service → python-app Pod
```

**NLB가 자동 생성되는 원리:**

Nginx Ingress Controller를 `type: LoadBalancer`로 설치하면,  
AWS Cloud Controller Manager가 이를 감지하고 NLB를 자동으로 프로비저닝합니다.

```yaml
# Nginx Ingress Service (설치 시 자동 구성)
kind: Service
spec:
  type: LoadBalancer       # ← 이 설정이 NLB 생성 트리거
  # AWS 어노테이션으로 NLB 지정
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
```

#### ALB vs NLB — 이 프로젝트에서 NLB + Nginx를 선택한 이유

| 항목 | ALB (Application LB) | NLB (Network LB) |
|------|---------------------|-----------------|
| **계층** | L7 (HTTP/HTTPS 인식) | L4 (TCP/UDP) |
| **라우팅** | URL 경로, 헤더 기반 | IP + 포트 기반 |
| **K8s 연동** | AWS ALB Ingress Controller | Nginx Ingress Controller |
| **설정 복잡도** | 높음 (IRSA, IAM 정책 필요) | 낮음 (자동 프로비저닝) |
| **경로 재작성** | 제한적 | Nginx에서 처리 가능 |
| **로컬 환경 일관성** | ❌ 로컬과 다름 | ✅ 로컬 Nginx와 동일 |

**이 프로젝트의 선택: NLB + Nginx Ingress**

```
이유 1: 로컬 k3s와 Ingress 설정 100% 동일 → 매니페스트 변경 최소화
이유 2: nginx.ingress.kubernetes.io/rewrite-target 어노테이션 그대로 재사용
이유 3: ALB Ingress Controller는 IRSA(IAM Role) 설정이 추가로 필요해 복잡도 증가
이유 4: 교육 목적에서 일관성이 학습에 더 유리

실무에서는 ALB를 선호하는 경우도 많습니다 (WAF 연동, 인증서 관리 등).
이 가이드는 교육 단순화를 위해 NLB+Nginx를 채택합니다.
```

---

### 4. eksctl의 역할

#### eksctl이란?

`eksctl`은 AWS 공식 EKS CLI 도구입니다.  
명령 한 줄로 EKS 클러스터 생성에 필요한 **수십 개의 AWS 리소스를 자동으로 생성**합니다.

```bash
# 이 명령 하나로 아래 모든 리소스가 생성됩니다
eksctl create cluster   --name my-cluster   --region ap-northeast-2   --nodes 2   --with-oidc
```

#### CloudFormation 기반 자동화

eksctl은 내부적으로 **AWS CloudFormation**을 사용합니다.  
CloudFormation은 AWS 리소스를 코드(YAML/JSON)로 정의하고 일괄 생성/삭제하는 서비스입니다.

```
eksctl create cluster 실행 시 자동 생성되는 리소스:

CloudFormation Stack 1: eksctl-<name>-cluster
  ├── VPC
  ├── Public Subnet × 2 (가용영역 분산)
  ├── Private Subnet × 2
  ├── Internet Gateway
  ├── NAT Gateway
  ├── Route Table
  ├── Security Group (Control Plane용)
  ├── EKS Cluster (Control Plane)
  └── IAM Role (EKS 서비스 역할)

CloudFormation Stack 2: eksctl-<name>-nodegroup-<ng-name>
  ├── EC2 Auto Scaling Group
  ├── EC2 인스턴스 (t3.medium × 2)
  ├── Security Group (Worker Node용)
  ├── IAM Role (Node Group 역할)
  └── Launch Template
```

**CloudFormation의 장점: 일괄 삭제**

```bash
# eksctl delete cluster 실행 시
# CloudFormation Stack이 삭제되면서
# 위에서 생성된 모든 리소스가 자동으로 함께 삭제됩니다.
# → 비용 누락 방지에 매우 중요!
eksctl delete cluster --name my-cluster --region ap-northeast-2
```

#### `--with-oidc` 옵션의 의미

```
--with-oidc = OIDC(OpenID Connect) Provider를 EKS 클러스터에 연결

왜 필요한가?
  Kubernetes Pod가 AWS 서비스(S3, DynamoDB 등)에 접근하려면
  AWS IAM 인증이 필요합니다.

  일반적 방법: EC2 IAM Role → 노드 위의 모든 Pod에 동일 권한 부여 (보안 취약)

  IRSA (IAM Roles for Service Accounts):
  → Pod별로 개별 IAM Role을 부여하는 더 안전한 방식
  → OIDC Provider가 있어야 IRSA 사용 가능

이 실습에서 --with-oidc를 넣는 이유:
  현재 실습에서 IRSA를 직접 사용하지 않더라도,
  추후 S3/DynamoDB 연동, ALB Ingress Controller 설치 등
  고급 실습으로 확장할 때 필요하므로 미리 설정합니다.
```

```
OIDC 동작 방식 (참고):

[Pod] → "나는 ServiceAccount 'java-app-sa'야"
  → OIDC Token 발급
  → AWS IAM에 Token 검증 요청
  → IAM이 OIDC Provider를 통해 검증
  → 해당 ServiceAccount에 매핑된 IAM Role 권한 부여
  → Pod가 AWS 서비스 접근 가능
```

---

### 5. 비용 관리 ⚠️

> 🚨 **AWS EKS는 실제 비용이 발생합니다.**  
> 이 섹션을 반드시 읽고, 실습 후 리소스 삭제를 잊지 마세요.

#### EKS 비용 구조

EKS를 사용하면 세 가지 항목에서 비용이 발생합니다.

```
비용 = Control Plane 비용 + Worker Node(EC2) 비용 + 네트워킹 비용
```

**① EKS Control Plane 비용**

| 항목 | 비용 |
|------|------|
| EKS 클러스터 1개 | **$0.10/시간** ($72/월) |

Control Plane은 클러스터가 존재하는 한 계속 과금됩니다.  
클러스터가 비어 있어도(Pod 없어도) 요금이 부과됩니다.

**② Worker Node (EC2) 비용**

| 인스턴스 타입 | 시간당 | 하루 (24시간) | 한 달 |
|-------------|--------|------------|------|
| t3.medium × 1 | $0.0416 | $1.00 | $30 |
| t3.medium × 2 | $0.0832 | $2.00 | $60 |

**③ 네트워킹 비용**

| 항목 | 비용 |
|------|------|
| NLB (Network Load Balancer) | $0.008/시간 + 데이터 처리 비용 |
| NAT Gateway | $0.045/시간 + 데이터 비용 |
| 데이터 전송 (아웃바운드) | $0.09/GB |

#### 예상 비용 (실습 기준)

| 시나리오 | Control Plane | EC2 × 2 | NLB | NAT GW | **합계** |
|---------|-------------|--------|-----|--------|--------|
| **1시간 실습** | $0.10 | $0.08 | $0.01 | $0.05 | **~$0.24** |
| **반나절 (8시간)** | $0.80 | $0.67 | $0.06 | $0.36 | **~$1.89** |
| **하루 (24시간)** | $2.40 | $2.00 | $0.19 | $1.08 | **~$5.67** |
| **일주일 방치** | $16.80 | $14.00 | $1.34 | $7.56 | **~$39.70** |
| **한 달 방치** | $72 | $60 | $5.76 | $32.40 | **~$170** |

> 💡 서울 리전(ap-northeast-2) 기준, 프리 티어 미적용, 2025년 기준 요금

#### 실습 후 반드시 리소스 삭제해야 하는 이유

```
❌ 클러스터를 방치했을 때:
  → 아무것도 배포하지 않아도 클러스터 자체 비용 발생
  → NAT Gateway는 시간당 과금 (한 달 ~$32)
  → NLB도 시간당 과금
  → EC2는 Stopped 상태여도 EBS 볼륨 비용 발생

✅ 올바른 실습 종료 절차:
  Step 12: eksctl delete cluster (모든 리소스 일괄 삭제)
  → CloudFormation Stack 삭제로 관련 리소스 전체 정리
  → 삭제 완료까지 15~20분 소요
  → AWS 콘솔에서 리소스 잔존 여부 최종 확인 권장
```

#### AWS 비용 알림 설정 권장 🔔

실습 전에 AWS Billing 알림을 설정해두면 예상치 못한 비용을 방지할 수 있습니다.

```
설정 방법 (AWS 콘솔):
1. AWS 콘솔 → 우측 상단 계정명 → "결제 대시보드"
2. 좌측 메뉴 → "결제 기본 설정"
3. "결제 알림 받기" 체크 → 저장
4. CloudWatch → 경보 → "결제 경보 생성"
5. 임계값: $5 또는 $10 (원하는 금액)
6. 이메일 알림 설정

→ 설정 비용: 무료
→ 효과: 예상치 못한 비용 발생 즉시 이메일 수신
```

#### 비용 절감 팁

```
✅ 실습 직전에 클러스터 생성 (최대한 짧은 시간 사용)
✅ 실습 완료 즉시 eksctl delete cluster 실행
✅ AWS 콘솔 → EC2 → 로드밸런서: NLB 잔존 여부 확인
✅ AWS 콘솔 → CloudFormation: 스택 완전 삭제 확인
✅ AWS 콘솔 → VPC → NAT Gateway: 삭제 확인
✅ AWS 비용 탐색기로 일별 비용 모니터링

❌ 금요일 저녁 실습 시작 후 주말에 삭제 잊지 않기
❌ 학습 목적으로 클러스터 장기간 유지하지 않기
```

---

---

## 🛠️ 실습 단계

> ⚠️ **사전 조건**:
> - 07 가이드(GitOps 배포 실습)까지 완료된 상태
> - AWS 계정 보유 및 EKS 사용 권한 있음
> - AWS 비용 발생 인지 (`Step 12` 정리 필수)

---

### Step 1: 전환 전 로컬 k3s 검증 체크리스트 확인

EKS로 전환하기 전에 로컬 환경에서 모든 항목이 검증되었는지 확인합니다.  
**아래 8개 항목이 모두 ✅여야 EKS 전환을 진행합니다.**

```bash
cd ~/workspace/git-argocd-lecture

echo "=== 전환 전 검증 체크리스트 ==="

echo ""
echo "① CI 파이프라인 상태 확인 (GitHub Actions)"
echo "   → https://github.com/$GITHUB_USERNAME/git-argocd-lecture/actions 에서 확인"

echo ""
echo "② GHCR 이미지 존재 확인"
docker pull ghcr.io/$GITHUB_USERNAME/node-app:latest 2>&1 | tail -1

echo ""
echo "③ ArgoCD Application 상태"
kubectl get applications -n argocd 2>/dev/null || echo "  (로컬 클러스터 연결 필요)"

echo ""
echo "④⑤ 로컬 앱 헬스체크"
curl -sf http://apps.local/java/health && echo " java: OK" || echo " java: FAIL"
curl -sf http://apps.local/node/health && echo " node: OK" || echo " node: FAIL"
curl -sf http://apps.local/python/health && echo " python: OK" || echo " python: FAIL"

echo ""
echo "⑥ 이미지 태그 업데이트 → 자동 배포: 07 가이드에서 확인됨"
echo "⑦ Self-healing 확인: 07 가이드 Step8에서 확인됨"
echo "⑧ 롤백 확인: 07 가이드 Step9에서 확인됨"
```

```
체크리스트 (UC-ENV-001 기반):
☐ ① 3개 앱 CI 파이프라인 ✅ 통과 (GitHub Actions 워크플로우)
☐ ② GHCR에 이미지 정상 존재 (docker pull 성공)
☐ ③ ArgoCD 3개 앱 Synced + Healthy
☐ ④ GET /java/health → "status": "ok"
☐ ⑤ GET /node/health, /python/health → 모두 ok
☐ ⑥ 이미지 태그 업데이트 → ArgoCD 자동 배포 확인 (07 가이드)
☐ ⑦ Self-healing 동작 확인 (kubectl scale → ArgoCD 복원)
☐ ⑧ 롤백 동작 확인 (git revert)
```

✅ **확인**: 8개 항목 전체 체크 완료 후 Step 2로 진행

---

### Step 2: AWS CLI + eksctl 설치 및 설정

**2-1. AWS CLI 설치**

```bash
# macOS (Homebrew)
brew install awscli

# 버전 확인
aws --version
```

```
예상 출력:
aws-cli/2.x.x Python/3.x.x Darwin/...
```

**2-2. eksctl 설치**

```bash
# macOS (Homebrew)
brew tap weaveworks/tap
brew install weaveworks/tap/eksctl

# 버전 확인
eksctl version
```

```
예상 출력:
0.x.x
```

**2-3. AWS 자격증명 설정**

```bash
# AWS IAM 자격증명 설정 (Access Key ID + Secret Access Key 입력)
aws configure
```

```
입력 항목:
AWS Access Key ID [None]: AKIAIOSFODNN7EXAMPLE
AWS Secret Access Key [None]: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
Default region name [None]: ap-northeast-2          ← 서울 리전
Default output format [None]: json
```

> 💡 **IAM 권한 요구사항**: EKS 클러스터 생성을 위해 아래 권한이 필요합니다.  
> - `AmazonEKSClusterPolicy`  
> - `AmazonEC2FullAccess`  
> - `IAMFullAccess`  
> - `AWSCloudFormationFullAccess`  
> 권한이 없으면 AWS 콘솔에서 IAM 사용자에게 추가하거나 관리자에게 요청하세요.

```bash
# 자격증명 확인
aws sts get-caller-identity
```

```
예상 출력:
{
    "UserId": "AIDAEXAMPLE",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/your-username"
}
```

✅ **확인**: `aws --version`, `eksctl version` 출력 확인, `aws sts get-caller-identity` 정상 응답

---

### Step 3: EKS 클러스터 생성

```bash
# 환경변수 설정
export CLUSTER_NAME="argocd-lecture"
export AWS_REGION="ap-northeast-2"

echo "클러스터 생성 시작: $CLUSTER_NAME (리전: $AWS_REGION)"
echo "⏱️ 약 15~20분 소요됩니다..."

# EKS 클러스터 생성
eksctl create cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed \
  --with-oidc \
  --ssh-access=false
```

```
예상 출력 (약 15~20분):
2026-04-04 xx:xx:xx [ℹ]  eksctl version 0.x.x
2026-04-04 xx:xx:xx [ℹ]  using region ap-northeast-2
2026-04-04 xx:xx:xx [ℹ]  setting availability zones to [ap-northeast-2a ap-northeast-2c]
2026-04-04 xx:xx:xx [ℹ]  subnets for ap-northeast-2a: Public:192.168.0.0/19  Private:192.168.64.0/19
2026-04-04 xx:xx:xx [ℹ]  subnets for ap-northeast-2c: Public:192.168.32.0/19 Private:192.168.96.0/19
2026-04-04 xx:xx:xx [ℹ]  nodegroup "standard-workers" will use ubuntu2004 AMI
...
2026-04-04 xx:xx:xx [✔]  EKS cluster "argocd-lecture" in "ap-northeast-2" region is ready
```

> ⏳ **기다리는 동안**: eksctl은 CloudFormation 스택을 생성합니다.  
> AWS 콘솔 → CloudFormation에서 진행 상황을 확인할 수 있습니다.

✅ **확인**: `EKS cluster "argocd-lecture" ... is ready` 메시지 출력

---

### Step 4: kubeconfig 업데이트 + 클러스터 연결 확인

```bash
# kubeconfig 업데이트 (EKS 컨텍스트 추가)
aws eks update-kubeconfig \
  --name $CLUSTER_NAME \
  --region $AWS_REGION

# 현재 컨텍스트 확인
kubectl config current-context
```

```
예상 출력:
Added new context arn:aws:eks:ap-northeast-2:123456789012:cluster/argocd-lecture to ...

arn:aws:eks:ap-northeast-2:123456789012:cluster/argocd-lecture
```

```bash
# 노드 상태 확인
kubectl get nodes -o wide
```

```
예상 출력:
NAME                                           STATUS   ROLES    AGE   VERSION
ip-192-168-xx-xx.ap-northeast-2.compute...    Ready    <none>   2m    v1.28.x
ip-192-168-xx-xx.ap-northeast-2.compute...    Ready    <none>   2m    v1.28.x
```

```bash
# 컨텍스트 목록 확인 (로컬 + EKS 둘 다 있음)
kubectl config get-contexts
```

```
예상 출력:
CURRENT   NAME                                                          CLUSTER
*         arn:aws:eks:ap-northeast-2:...:cluster/argocd-lecture        argocd-lecture  ← EKS (현재)
          rancher-desktop                                               rancher-desktop  ← 로컬
```

> 💡 **컨텍스트 전환** 방법:
> ```bash
> kubectl config use-context rancher-desktop      # 로컬로 전환
> kubectl config use-context arn:aws:eks:...      # EKS로 전환
> ```

✅ **확인**: 노드 2개 `Ready` 상태, 현재 컨텍스트가 EKS 클러스터

---

### Step 5: Nginx Ingress Controller 설치 (AWS 버전)

AWS 환경에서는 Nginx가 NLB(Network Load Balancer)를 자동 프로비저닝합니다.

```bash
# AWS 전용 Nginx Ingress Controller 설치
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/aws/deploy.yaml

# 설치 대기 (NLB 프로비저닝 포함 3~5분)
echo "Nginx Ingress Controller 및 NLB 프로비저닝 대기 중..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=300s
```

```
예상 출력:
namespace/ingress-nginx created
...
pod/ingress-nginx-controller-xxx condition met
```

```bash
# NLB DNS 이름 확인 (EXTERNAL-IP → AWS에서는 hostname으로 제공)
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

```
예상 출력:
NAME                       TYPE           CLUSTER-IP    EXTERNAL-IP                                    PORT(S)
ingress-nginx-controller   LoadBalancer   10.100.xx.x   xxxxx.elb.ap-northeast-2.amazonaws.com         80:xxxxx/TCP,443:xxxxx/TCP
                                                         ↑ 이 DNS 이름을 기록해두세요
```

```bash
# NLB DNS 이름 환경변수로 저장
NLB_DNS=$(kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "NLB DNS: $NLB_DNS"

# NLB DNS 해석 테스트 (전파까지 1~3분 소요)
nslookup $NLB_DNS | grep Address | tail -1
```

```
예상 출력:
NLB DNS: xxxxx.elb.ap-northeast-2.amazonaws.com
Address: 52.xxx.xxx.xxx
```

> ⏳ NLB는 AWS에서 DNS 이름으로 제공됩니다. 전파까지 1~3분 소요됩니다.

✅ **확인**: `EXTERNAL-IP`에 `.elb.ap-northeast-2.amazonaws.com` DNS 이름 존재

---

### Step 6: ArgoCD 설치 (EKS 클러스터)

로컬과 동일한 방법으로 EKS 클러스터에 ArgoCD를 설치합니다.

```bash
# argocd 네임스페이스 생성 + 설치
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 파드 Ready 대기 (약 2~3분)
echo "ArgoCD 기동 대기 중..."
kubectl wait --namespace argocd \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=argocd-server \
  --timeout=300s

# 전체 파드 상태 확인
kubectl get pods -n argocd
```

```
예상 출력:
argocd-application-controller-xxx      1/1   Running
argocd-applicationset-controller-xxx   1/1   Running
argocd-dex-server-xxx                  1/1   Running
argocd-notifications-controller-xxx    1/1   Running
argocd-redis-xxx                       1/1   Running
argocd-repo-server-xxx                 1/1   Running
argocd-server-xxx                      1/1   Running
```

```bash
# ArgoCD UI 접근 (포트포워딩)
kubectl port-forward svc/argocd-server -n argocd 8443:443 &
echo "ArgoCD UI: https://localhost:8443"

# 초기 admin 비밀번호 확인
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo ""
```

```
예상 출력:
XxXxXxXxXxXxXxXx   ← 브라우저에서 admin 로그인 시 사용
```

✅ **확인**: 7개 파드 `1/1 Running`, ArgoCD UI 접근 성공

---

### Step 7: GHCR imagePullSecret 생성

EKS 환경에서는 `imagePullSecret`이 **필수**입니다 (로컬 환경과 달리 자동 인증 없음).

```bash
# apps 네임스페이스 생성
kubectl create namespace apps

# regcred Secret 생성 (deployment.yaml에 명시된 이름과 동일)
kubectl create secret docker-registry regcred \
  --docker-server=ghcr.io \
  --docker-username=$GITHUB_USERNAME \
  --docker-password=$GITHUB_TOKEN \
  --namespace=apps

# 생성 확인
kubectl get secret regcred -n apps
```

```
예상 출력:
namespace/apps created

NAME      TYPE                             DATA   AGE
regcred   kubernetes.io/dockerconfigjson   1      x seconds
```

> 💡 **GHCR 이미지가 Public이더라도** EKS에서는 명시적으로 Secret을 생성하는 것을 권장합니다.  
> (향후 Private 전환 대비 + 일관성 유지)

✅ **확인**: `regcred` Secret 존재 확인

---

### Step 8: 매니페스트 수정 (Ingress host 변경)

로컬 `apps.local` 도메인을 NLB DNS 이름으로 변경합니다.  
**EKS 전용 브랜치를 만들어 관리합니다.**

```bash
cd ~/workspace/git-argocd-lecture

# EKS 전용 브랜치 생성
git checkout -b eks-deploy
git status
```

**8-1. NLB DNS 이름 확인**

```bash
# 환경변수 재확인
echo "NLB DNS: $NLB_DNS"

# 없으면 다시 조회
NLB_DNS=$(kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "NLB DNS: $NLB_DNS"
```

**8-2. Ingress 매니페스트 host 변경**

```bash
# 3개 앱 Ingress의 host를 NLB DNS로 일괄 변경
for app in java-app node-app python-app; do
  sed -i '' "s|host: apps.local|host: $NLB_DNS|g" \
    manifests/$app/ingress.yaml
  echo "Updated: manifests/$app/ingress.yaml"
done

# 변경 확인
grep "host:" manifests/java-app/ingress.yaml \
             manifests/node-app/ingress.yaml \
             manifests/python-app/ingress.yaml
```

```
예상 출력:
Updated: manifests/java-app/ingress.yaml
Updated: manifests/node-app/ingress.yaml
Updated: manifests/python-app/ingress.yaml

manifests/java-app/ingress.yaml:      host: xxxxx.elb.ap-northeast-2.amazonaws.com
manifests/node-app/ingress.yaml:      host: xxxxx.elb.ap-northeast-2.amazonaws.com
manifests/python-app/ingress.yaml:    host: xxxxx.elb.ap-northeast-2.amazonaws.com
```

**변경 전/후 비교**

```yaml
# 변경 전 (로컬 k3s)
spec:
  rules:
    - host: apps.local                              # 로컬 /etc/hosts 도메인
      http:
        paths:
          - path: /java(/|$)(.*)
            ...

# 변경 후 (EKS)
spec:
  rules:
    - host: xxxxx.elb.ap-northeast-2.amazonaws.com  # AWS NLB DNS
      http:
        paths:
          - path: /java(/|$)(.*)
            ...

# ✅ 변경 없는 항목:
#   ingressClassName: nginx        (Nginx 유지)
#   rewrite-target annotation      (그대로 재사용)
#   path 패턴 /java(/|$)(.*)      (그대로 재사용)
#   backend service/port           (그대로 재사용)
```

**8-3. 변경 커밋 + push (eks-deploy 브랜치)**

```bash
git add manifests/java-app/ingress.yaml \
        manifests/node-app/ingress.yaml \
        manifests/python-app/ingress.yaml

git commit -m "deploy(eks): update ingress host to NLB DNS for EKS environment"
git push origin eks-deploy
```

✅ **확인**: 3개 Ingress의 host가 NLB DNS 이름으로 변경됨

---

### Step 9: ArgoCD Application CR 적용 (EKS)

ArgoCD Application CR의 `targetRevision`을 `eks-deploy` 브랜치로 변경합니다.

```bash
cd ~/workspace/git-argocd-lecture

# ArgoCD Application CR의 targetRevision을 eks-deploy 브랜치로 변경
for app in java-app node-app python-app; do
  sed -i '' \
    "s|targetRevision: main|targetRevision: eks-deploy|g" \
    argocd/applications/$app.yaml
  echo "Updated: argocd/applications/$app.yaml"
done

# repoURL이 본인 저장소인지 확인 (06 가이드에서 이미 변경했어야 함)
grep "repoURL\|targetRevision" argocd/applications/java-app.yaml
```

```
예상 출력:
    repoURL: https://github.com/<your-username>/git-argocd-lecture.git
    targetRevision: eks-deploy
```

```bash
# AppProject 먼저 적용
kubectl apply -f argocd/applications/project.yaml

# 3개 Application CR 적용
kubectl apply -f argocd/applications/java-app.yaml
kubectl apply -f argocd/applications/node-app.yaml
kubectl apply -f argocd/applications/python-app.yaml

# Application 상태 확인 (1~3분 대기)
echo "ArgoCD 동기화 대기 중..."
sleep 30
kubectl get applications -n argocd
```

```
예상 출력:
NAME         SYNC STATUS   HEALTH STATUS
java-app     Synced        Healthy
node-app     Synced        Healthy
python-app   Synced        Healthy
```

> ⏳ 처음에는 `OutOfSync` 상태입니다. ArgoCD가 Git을 감지하고 자동 동기화합니다.  
> 최대 3분 소요. Java 앱은 JVM 기동으로 추가 시간이 필요합니다.

✅ **확인**: 3개 Application 모두 `Synced + Healthy`

---

### Step 10: 3개 앱 배포 확인 + 헬스체크

```bash
# 파드 상태 확인
kubectl get pods -n apps -o wide
```

```
예상 출력:
NAME                          READY   STATUS    NODE
java-app-xxx-xxx              1/1     Running   ip-192-168-xx-xx...
node-app-xxx-xxx              1/1     Running   ip-192-168-xx-xx...
python-app-xxx-xxx            1/1     Running   ip-192-168-xx-xx...
```

```bash
# 서비스 + Ingress 상태 확인
kubectl get svc -n apps
kubectl get ingress -n apps
```

```
예상 출력 (ingress):
NAME                CLASS   HOSTS                                    ADDRESS                                    PORTS
java-app-ingress    nginx   xxxxx.elb.ap-northeast-2.amazonaws.com  xxxxx.elb.ap-northeast-2.amazonaws.com    80
node-app-ingress    nginx   xxxxx.elb.ap-northeast-2.amazonaws.com  xxxxx.elb.ap-northeast-2.amazonaws.com    80
python-app-ingress  nginx   xxxxx.elb.ap-northeast-2.amazonaws.com  xxxxx.elb.ap-northeast-2.amazonaws.com    80
```

```bash
# NLB DNS 재확인 (환경변수 없으면 재조회)
NLB_DNS=$(kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# 헬스체크 — 3개 앱 전체
echo "=== EKS 앱 헬스체크 ==="
curl -sf http://$NLB_DNS/java/health | python3 -m json.tool
curl -sf http://$NLB_DNS/node/health | python3 -m json.tool
curl -sf http://$NLB_DNS/python/health | python3 -m json.tool
```

```
예상 출력:
{
    "status": "ok",
    "app": "java-app",
    "version": "..."
}
{
    "status": "ok",
    "app": "node-app",
    "version": "..."
}
{
    "status": "ok",
    "app": "python-app",
    "version": "..."
}
```

```bash
# 기본 정보 API 확인
curl -sf http://$NLB_DNS/java | python3 -m json.tool
curl -sf http://$NLB_DNS/node | python3 -m json.tool
curl -sf http://$NLB_DNS/python | python3 -m json.tool
```

```
예상 출력 (java):
{
    "app": "java-app",
    "version": "...",
    "language": "Java",
    "framework": "Spring Boot 3.x",
    "port": 8080,
    "environment": "production"
}
```

✅ **확인**: 3개 앱 모두 `"status": "ok"` 응답, EKS 배포 완료

---

### Step 11: CI push → EKS 자동 배포 E2E 확인

동일한 CI 파이프라인이 EKS 환경에서도 동작하는지 검증합니다.

**11-1. 코드 변경 + push**

```bash
cd ~/workspace/git-argocd-lecture

# 현재 eks-deploy 브랜치인지 확인
git branch --show-current

# node-app 버전 식별용 변경
echo "# EKS E2E Test $(date)" >> app/node-app/README.md

git add app/node-app/README.md
git commit -m "test(eks): trigger CI for E2E verification on EKS"
git push origin eks-deploy
```

> ⚠️ **주의**: 현재 CI 워크플로우는 `main` 브랜치 push 시에만 이미지를 GHCR에 푸시합니다.  
> `eks-deploy` 브랜치 push는 `build-and-test`만 실행됩니다.  
> EKS E2E를 위한 이미지 푸시는 아래 방법으로 진행합니다.

**11-2. main 브랜치 병합 + 이미지 푸시**

```bash
# main 브랜치로 전환 후 eks-deploy 내용 병합
git checkout main
git merge eks-deploy
git push origin main
```

```
예상 결과:
GitHub Actions → CI - Node.js App 실행
  build-and-test ✅ → docker-build-push ✅
  GHCR에 새 SHA 이미지 등록
```

**11-3. 새 SHA로 매니페스트 업데이트**

```bash
git checkout eks-deploy

# 새 이미지 SHA 확인
NEW_SHA=$(git rev-parse HEAD)
SHORT_SHA=$(git rev-parse --short HEAD)

# manifests 업데이트
sed -i '' \
  "s|image: ghcr.io/anbyounghyun/node-app:.*|image: ghcr.io/$GITHUB_USERNAME/node-app:$NEW_SHA|" \
  manifests/node-app/deployment.yaml
sed -i '' \
  "s|value: \"PLACEHOLDER_SHA\"|value: \"$SHORT_SHA\"|" \
  manifests/node-app/deployment.yaml

git add manifests/node-app/deployment.yaml
git commit -m "deploy(eks,node-app): update image to $SHORT_SHA"
git push origin eks-deploy
```

**11-4. ArgoCD EKS 동기화 확인**

```bash
# 컨텍스트가 EKS인지 확인
kubectl config current-context

# ArgoCD 동기화 대기
sleep 60
kubectl get applications -n argocd

# 새 파드 확인
kubectl get pods -n apps -l app=node-app

# 새 버전 API 응답 확인
curl -sf http://$NLB_DNS/node | python3 -m json.tool
```

```
예상 출력:
{
    "app": "node-app",
    "version": "abc1234",    ← 새 SHA 반영됨
    "language": "Node.js",
    ...
}
```

✅ **확인**: EKS 환경에서 코드 변경 → CI → 이미지 → ArgoCD → 배포 전체 흐름 동작

---

### Step 12: 리소스 정리 (⚠️ 필수 — 비용 방지)

> 🚨 **실습 종료 후 반드시 실행하세요.**  
> EKS 클러스터를 방치하면 **일 $4~5(한화 약 6,000~7,000원)의 비용**이 계속 발생합니다.

**12-1. 정리 전 최종 상태 확인**

```bash
# 현재 실행 중인 리소스 확인
echo "=== EKS 클러스터 리소스 현황 ==="
kubectl get all -n apps
kubectl get all -n argocd
kubectl get all -n ingress-nginx

# AWS 콘솔에서 확인할 URL
echo "EKS 콘솔: https://ap-northeast-2.console.aws.amazon.com/eks/home?region=ap-northeast-2#/clusters"
echo "EC2 콘솔: https://ap-northeast-2.console.aws.amazon.com/ec2/home?region=ap-northeast-2#Instances:"
```

**12-2. ArgoCD Application 삭제 (프로비저닝된 리소스 정리)**

```bash
# ArgoCD Application 삭제 (cascade: apps 네임스페이스 리소스도 함께 삭제)
kubectl delete application java-app node-app python-app -n argocd
kubectl delete appproject cicd-project -n argocd

# apps 네임스페이스 삭제 확인
kubectl get pods -n apps
```

```
예상 출력:
application.argoproj.io "java-app" deleted
application.argoproj.io "node-app" deleted
application.argoproj.io "python-app" deleted
(apps 네임스페이스 파드 없음)
```

**12-3. EKS 클러스터 삭제**

```bash
echo "⚠️ EKS 클러스터 삭제를 시작합니다. 약 10~15분 소요됩니다..."
echo "삭제 대상: $CLUSTER_NAME (리전: $AWS_REGION)"
echo ""

eksctl delete cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --wait
```

```
예상 출력 (10~15분):
2026-04-04 xx:xx:xx [ℹ]  deleting EKS cluster "argocd-lecture"
2026-04-04 xx:xx:xx [ℹ]  deleting Fargate profile ...
2026-04-04 xx:xx:xx [ℹ]  deleted 0 Fargate profile(s)
2026-04-04 xx:xx:xx [ℹ]  cleaning up AWS load balancers created by Kubernetes objects ...
2026-04-04 xx:xx:xx [ℹ]  2 nodegroups (standard-workers) were deleted
2026-04-04 xx:xx:xx [ℹ]  1 task: { delete cluster control plane ... }
2026-04-04 xx:xx:xx [✔]  all cluster resources were deleted
```

**12-4. 삭제 완료 검증**

```bash
# EKS 클러스터 목록 확인 (argocd-lecture가 없어야 함)
aws eks list-clusters --region $AWS_REGION

# EC2 인스턴스 확인 (t3.medium 인스턴스가 없어야 함)
aws ec2 describe-instances \
  --region $AWS_REGION \
  --filters "Name=tag:alpha.eksctl.io/cluster-name,Values=$CLUSTER_NAME" \
  --query 'Reservations[].Instances[].InstanceId' \
  --output text

# NLB 확인 (argocd-lecture 관련 NLB가 없어야 함)
aws elbv2 describe-load-balancers \
  --region $AWS_REGION \
  --query 'LoadBalancers[?contains(LoadBalancerName, `argocd`)].LoadBalancerName' \
  --output text
```

```
예상 출력:
{
    "clusters": []    ← argocd-lecture 없음
}
(빈 출력)             ← EC2 인스턴스 없음
(빈 출력)             ← NLB 없음
```

**12-5. kubeconfig 정리 (선택)**

```bash
# EKS 컨텍스트 제거
kubectl config delete-context \
  "arn:aws:eks:ap-northeast-2:$(aws sts get-caller-identity --query Account --output text):cluster/$CLUSTER_NAME"

# 로컬 컨텍스트로 복귀
kubectl config use-context rancher-desktop

# 컨텍스트 목록 확인
kubectl config get-contexts
```

**12-6. eks-deploy 브랜치 정리 (선택)**

```bash
cd ~/workspace/git-argocd-lecture

# ArgoCD Application targetRevision 원복 (main으로 복원)
git checkout main
git checkout argocd/applications/

# eks-deploy 브랜치 삭제 (선택)
git branch -d eks-deploy
git push origin --delete eks-deploy
```

✅ **확인**: `aws eks list-clusters` 결과 빈 배열 `{"clusters": []}`, EC2/NLB 리소스 없음

---

## ✅ 확인 체크리스트

### 전환 전

- [ ] 로컬 k3s 검증 8개 항목 전체 통과 (Step 1)
- [ ] AWS CLI + eksctl 설치 및 `aws sts get-caller-identity` 정상 응답

### EKS 구성

- [ ] EKS 클러스터 생성 완료, 노드 2개 `Ready` 상태 (Step 3~4)
- [ ] Nginx Ingress Controller NLB DNS 이름 확인 (Step 5)
- [ ] ArgoCD 7개 파드 `Running`, UI 접근 성공 (Step 6)
- [ ] `regcred` imagePullSecret 생성 완료 (Step 7)

### 매니페스트 + 배포

- [ ] Ingress host `apps.local` → NLB DNS 변경 + `eks-deploy` 브랜치 push (Step 8)
- [ ] ArgoCD Application 3개 `Synced + Healthy` (Step 9)
- [ ] `curl http://$NLB_DNS/java/health` → `"status": "ok"` (Step 10)
- [ ] `curl http://$NLB_DNS/node/health` → `"status": "ok"` (Step 10)
- [ ] `curl http://$NLB_DNS/python/health` → `"status": "ok"` (Step 10)
- [ ] CI push → GHCR 이미지 → ArgoCD EKS 배포 E2E 동작 (Step 11)

### 정리 (필수)

- [ ] `eksctl delete cluster --name argocd-lecture` 완료 (Step 12)
- [ ] `aws eks list-clusters` — 빈 목록 확인 (Step 12)
- [ ] EC2 인스턴스 없음 확인 (Step 12)
- [ ] NLB 없음 확인 (Step 12)

---

## 🎓 로컬 k3s와 EKS의 핵심 차이 정리

| 구분 | 로컬 k3s | AWS EKS | 변경 범위 |
|------|---------|---------|---------|
| **CI 파이프라인** | 동일 | 동일 | ❌ 변경 없음 |
| **이미지 레지스트리** | GHCR | GHCR | ❌ 변경 없음 |
| **ArgoCD** | k3s 내 설치 | EKS 내 재설치 | ⚠️ 재설치 |
| **Deployment/Service** | 동일 | 동일 | ❌ 변경 없음 |
| **Ingress class** | nginx | nginx | ❌ 변경 없음 |
| **Ingress host** | `apps.local` | NLB DNS | ⚠️ 최소 변경 |
| **외부 노출** | /etc/hosts 설정 | AWS NLB 자동 | ⚠️ 자동 처리 |
| **imagePullSecret** | 선택적 | **필수** | ⚠️ 추가 |
| **비용** | 무료 | 시간당 $0.19 | ⚠️ 정리 필수 |

> **결론**: GitOps 설계 덕분에 CI 파이프라인, 이미지 레지스트리, Git 저장소, Deployment, Service는 **변경 없이** 재사용됩니다.  
> 변경 범위는 **Ingress host 1줄 + ArgoCD 재설치**로 최소화됩니다.

---

**이전 단계**: [07. GitOps 배포 실습](07-gitops-deploy.md)
