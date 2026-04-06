# 07. GitOps 배포 실습 — 전체 파이프라인 연동

> 가이드 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 상태: ✅ 실습 단계 작성 완료  
> 이전 가이드: [06. Rancher Desktop + ArgoCD](06-rancher-argocd.md) | 다음 가이드: [09. AWS EKS 전환](09-aws-eks.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] 코드 변경 → CI → 이미지 푸시 → 매니페스트 업데이트 → ArgoCD 동기화 → 배포까지 전체 흐름을 직접 체험할 수 있다
- [ ] ArgoCD의 Self-healing 동작을 직접 확인하고 설명할 수 있다
- [ ] Git revert와 ArgoCD UI 롤백으로 이전 버전으로 복원할 수 있다

---

## 📖 이론

### 1. GitOps란? — 선언적 배포 vs 명령적 배포

**명령적 배포 (Imperative)**: "이렇게 해라"를 직접 지시

```bash
# 명령적 방식 — kubectl로 직접 지시
kubectl set image deployment/node-app node-app=ghcr.io/user/node-app:v2
kubectl scale deployment/node-app --replicas=3
kubectl rollout undo deployment/node-app
```

**선언적 배포 (Declarative)**: "이런 상태여야 한다"를 선언

```yaml
# 선언적 방식 — YAML로 원하는 상태를 기술
spec:
  replicas: 3
  template:
    spec:
      containers:
        - image: ghcr.io/user/node-app:v2
```

| 비교 항목 | 명령적 배포 | 선언적 배포 (GitOps) |
|---------|-----------|-------------------|
| 방식 | "무엇을 해라" 명령 | "어떤 상태여야 한다" 선언 |
| 이력 관리 | 명령을 기억해야 함 | Git 커밋으로 모든 변경 이력 보관 |
| 재현성 | 누가 언제 어떤 명령을 했는지 파악 어려움 | 언제든 같은 Git 상태로 재현 가능 |
| 감사 추적 | 어렵거나 불가능 | Git log로 누가, 언제, 무엇을 변경했는지 확인 |
| 장애 복구 | 이전 상태로 돌아가려면 수동 명령 필요 | `git revert` 한 번으로 자동 복구 |

#### Git = Single Source of Truth (단일 진실의 원천)

```
클러스터 상태 = Git 저장소에 선언된 상태

Git 저장소
  manifests/node-app/deployment.yaml
  └── image: ghcr.io/user/node-app:abc1234  ← 이것이 "진실"

클러스터 상태가 다르면?
  → 오류 (누군가 직접 변경했거나 배포 실패)
  → ArgoCD가 Git 상태로 복원
```

#### `kubectl apply` 직접 실행이 금지되는 이유

GitOps에서 **클러스터를 직접 수정하는 행위**는 원칙 위반입니다:

1. **이력 소실**: kubectl 명령은 Git에 기록되지 않음
2. **드리프트(drift) 발생**: Git과 클러스터 상태가 달라져 "무엇이 진실인가" 모호해짐
3. **재현 불가**: 장애 발생 시 동일한 상태로 재현이 어려움
4. **협업 문제**: 팀원이 현재 클러스터 상태를 알 수 없음

```
❌ 금지: kubectl apply -f (직접 배포)
❌ 금지: kubectl set image ... (직접 이미지 변경)
❌ 금지: kubectl scale ... (직접 스케일)

✅ 허용: Git 커밋 → ArgoCD가 자동 배포
```

---

### 2. ArgoCD의 동기화 모델 — Git vs 클러스터 상태 비교

ArgoCD는 **지속적으로 Git과 클러스터를 비교**합니다.

```
  ┌──────────────────────────┐      ┌──────────────────────────┐
  │  📄 Git 저장소            │      │  ☸️ K8s 클러스터          │
  │  manifests/node-app/     │      │  현재 실행 중인            │
  │  image: node-app:abc1234 │      │  image: node-app:xyz9012  │
  └──────────────────────────┘      └──────────────────────────┘
               │                                  │
               │ ◀─── ArgoCD 3분 주기 폴링 ──────│
               │                                  │
               └─────────────┬────────────────────┘
                             │ 비교 (diff)
                             ▼
                     ┌───────────────┐
                     │  🔍 불일치!   │
                     │  ⚠️ OutOfSync │
                     └───────────────┘
```

#### 상태 전환 흐름

```
  초기 배포 완료
       │
       ▼
  ┌──────────┐  Git 새 커밋               ┌────────────┐
  │  Synced  │ ────────────────────────> │ OutOfSync  │
  └──────────┘  (이미지 태그 변경)         └────────────┘
       ▲                                        │
       │                              auto-sync │
       │                            kubectl apply│
       │                                        ▼
  readinessProbe 통과               ┌──────────────────┐
  (새 Pod Ready)                    │   Progressing    │
       │                            └──────────────────┘
       └─────────────────────────────────────────┘│
                                                  │ Pod 기동 실패
                                                  ▼
                                         ┌──────────────┐
                                         │   Degraded   │
                                         └──────────────┘
                                                  │
                                     수동 수정 or rollback
                                                  │
                                                  └──→ OutOfSync
```

#### auto-sync, prune, selfHeal — 각각의 의미

```yaml
syncPolicy:
  automated:
    prune: true       # Git에서 파일이 삭제되면 클러스터에서도 삭제
    selfHeal: true    # 클러스터가 Git과 달라지면 자동으로 Git 상태로 복원
```

| 설정 | OFF일 때 | ON일 때 |
|------|---------|--------|
| `automated` | Git 변경 감지해도 수동 Sync 필요 | Git 변경 즉시 자동 배포 |
| `prune` | Git에서 Service 삭제해도 클러스터에 남음 | Git에서 삭제 → 클러스터에서도 삭제 |
| `selfHeal` | kubectl 직접 변경 → OutOfSync 표시만 | kubectl 직접 변경 → 자동으로 Git 상태 복원 |

> ⚠️ `prune: true`는 강력한 옵션입니다. 실수로 파일을 삭제하면 운영 중인 서비스가 내려갈 수 있습니다.  
> 이 프로젝트에서는 학습 목적이므로 활성화했지만, 프로덕션에서는 신중하게 설정하세요.

---

### 3. Self-healing의 가치 — 드리프트(Drift)가 위험한 이유

**드리프트(Drift)**: Git(선언된 상태)과 클러스터(실제 상태)가 달라지는 현상

```
[시나리오]: 새벽 3시에 장애 발생

운영자: "빨리 고쳐야 해!"
  → kubectl scale deployment node-app --replicas=5  ← 직접 수정
  → kubectl set image deployment/node-app ... ← 임시 이미지 적용

장애 해결 후:
  - Git에는 replicas: 1, 이미지: abc1234 (원래 상태)
  - 클러스터에는 replicas: 5, 이미지: test-build (임시 상태)
  
→ 드리프트 발생!
  - 팀원이 다음에 배포하면? → 원래 이미지로 되돌아감 (혼란)
  - 클러스터를 재현하려면? → 어떤 명령을 실행했는지 기억해야 함
  - 인프라 감사 시? → 실제 상태와 문서가 다름
```

**ArgoCD Self-healing이 드리프트를 방지하는 메커니즘**

```
  👤 운영자      ☸️ Kubernetes    🔄 ArgoCD      📄 Git
       │               │               │              │
       │─ kubectl scale ──────────────>│              │
       │  --replicas=5  │              │              │
       │               │               │              │
       │               │─ 상태 변경 감지 (replicas=5) ─>│
       │               │               │              │
       │               │               │─ 조회 ───────>│
       │               │               │<── replicas=1 ┤
       │               │               │              │
       │               │   불일치 감지! selfHeal: true  │
       │               │               │              │
       │               │<── kubectl apply (replicas=1) ─│
       │               │    강제 복원                  │
       │               │               │              │
       │               │─ 복원 완료 (≤30s) ────────────>│
```

이 덕분에 팀원 누구도 실수로 클러스터를 수동 변경해도,  
항상 Git이 정의한 상태로 자동 복귀됩니다.

---

### 4. 롤백 전략 — git revert가 권장되는 이유

**방법 비교**

```
  ┌───────────────────────────────────┐ ┌──────────────────────────────────┐
  │  ✅ 방법 A: Git revert (권장)     │ │  ⚠️ 방법 B: ArgoCD UI (긴급시)  │
  │                                   │ │                                  │
  │  1. git revert HEAD               │ │  1. ArgoCD UI                   │
  │     git push origin main          │ │     History → Rollback          │
  │  2. ArgoCD 감지 → 자동 배포       │ │  2. 클러스터 이전 이미지 복원    │
  │  3. Git 히스토리 보존             │ │                                  │
  │     (revert 이력 추적 가능)       │ │  ⚠️ Git 미반영                  │
  │                                   │ │  ⚠️ 다음 Sync 시 덮어쓰임       │
  │  ✅ GitOps 원칙 준수              │ │  ⚠️ 이력 추적 어려움             │
  │  ✅ 감사 추적 가능                │ │                                  │
  │  ✅ 팀원 공유 가능                │ │  반드시 git revert로 맞출 것     │
  └───────────────────────────────────┘ └──────────────────────────────────┘
```

**언제 어떤 방법을 쓰는가?**

| 상황 | 권장 방법 |
|------|---------|
| 일반적인 롤백 | `git revert` → push |
| 즉각적인 응급 복구 (git 느릴 때) | ArgoCD UI Rollback → 이후 git revert 보완 |
| 자동화된 롤백 (스크립트) | `argocd app rollback <app> <revision>` |

---

### 5. CI/CD 전체 흐름 — 코드 한 줄이 배포까지 가는 여정

```
  개발자      GitHub      CI(Actions)    GHCR      ArgoCD     K8s
    │            │              │           │           │         │
    │─① push ───>│              │           │           │         │
    │  main      │              │           │           │         │
    │  app/node-app 변경        │           │           │         │
    │            │─② ci-node.yml 트리거 ──────────────>│         │
    │            │              │           │           │         │
    │            │    Job1: npm ci + npm test ✅         │         │
    │            │    Job2: docker push ──────────────> │         │
    │            │              │        node-app:SHA   │         │
    │            │<─③ manifest 커밋 ──────>│            │         │
    │            │   image:NEW-SHA         │            │         │
    │            │              │          │  ④ 3분 폴링─>         │
    │            │              │          │  OutOfSync ──────────>│
    │            │              │          │            │─ apply ──>│
    │            │              │          │<── Pull ─────────────│
    │            │              │          │    ⑤ RollingUpdate   │
    │<──────────────── ⑥ 새 버전 응답 ──────────────────────────│
```

**핵심 포인트**: 개발자는 코드만 수정하고 Git에 push합니다.  
나머지는 CI(GitHub Actions)와 CD(ArgoCD)가 자동으로 처리합니다.  
클러스터에 직접 접근할 필요가 없습니다.

---


### 전체 GitOps 파이프라인 흐름

```
  ┌──────────────────────────────────────────┐
  │  ① 코드 수정                             │
  │     git commit + push main               │
  └──────────────────────────────────────────┘
                        │
                        ▼
  ┌──────────────────────────────────────────┐
  │  ② GitHub Actions CI 트리거              │
  │     app/node-app/** → ci-node.yml        │
  └──────────────────────────────────────────┘
                        │
                        ▼
  ┌──────────────────────────────────────────┐
  │  Job1: build-and-test                    │
  │     checkout → npm ci → npm test         │
  └──────────────────────────────────────────┘
                        │
                 테스트 통과?
               ┌─────────┴─────────┐
           ✅ 성공              ❌ 실패
               │                   │
               ▼                   ▼
  ┌───────────────────┐   ┌──────────────────┐
  │ Job2:             │   │ CI 중단          │
  │ docker push       │   │ GHCR 변경 없음   │
  │ app:SHA + latest  │   └──────────────────┘
  └───────────────────┘
               │
               ▼
  ┌──────────────────────────────────────────┐
  │  ③ 매니페스트 업데이트                   │
  │     image: node-app:new-sha              │
  │     git commit + push                    │
  └──────────────────────────────────────────┘
               │
               ▼
  ┌──────────────────────────────────────────┐
  │  ④ ArgoCD 감지 (3분 폴링)                │
  │     OutOfSync → auto-sync                │
  └──────────────────────────────────────────┘
               │
               ▼
  ┌──────────────────────────────────────────┐
  │  ⑤ K8s RollingUpdate                    │
  │     신규 Pod → readinessProbe → 구 Pod   │
  └──────────────────────────────────────────┘
               │
               ▼
  ┌──────────────────────────────────────────┐
  │  ⑥ 배포 완료 ✅                          │
  │     Synced + Healthy                     │
  └──────────────────────────────────────────┘
```

### Self-healing 원리

```
kubectl scale deployment node-app --replicas=5 (직접 수정)
    │
    ▼
ArgoCD Application Controller (주기적 상태 비교)
    └── 클러스터 상태 (replicas=5) ≠ Git 상태 (replicas=1)
              │
              ▼ selfHeal: true
    kubectl apply -f manifests/node-app/deployment.yaml
              │
              ▼
    replicas=1로 자동 복원 (30초 이내)
```

### 롤백 방법 비교

| 방법 | 원리 | 특징 |
|------|------|------|
| **Git revert** (권장) | 이전 커밋 되돌리기 → push → ArgoCD 감지 | GitOps 원칙 준수, 이력 보존 |
| ArgoCD UI Rollback | ArgoCD가 이전 sync 버전으로 복원 | 빠르지만 Git과 일시적 불일치 |
| ArgoCD CLI | `argocd app rollback <app> <revision>` | 자동화 가능 |

---

## 🛠️ 실습 단계

> ⚠️ **사전 조건**:  
> - 06 가이드 완료 (ArgoCD 설치, 3개 앱 `Synced + Healthy`)  
> - `curl http://apps.local/node` 정상 응답 확인
> - `GITHUB_USERNAME` 환경변수 설정됨

---

### Step 1: 초기 상태 확인

전체 파이프라인 실습 전 현재 상태를 기록합니다.

```bash
# 현재 배포된 이미지 버전 확인
kubectl get deployment node-app -n apps \
  -o jsonpath='{.spec.template.spec.containers[0].image}' && echo ""

# 현재 API 응답 확인 (version 필드 기록)
curl -s http://apps.local/node | python3 -m json.tool

# ArgoCD 앱 상태 확인
kubectl get applications -n argocd
```

```
예상 출력:
ghcr.io/anbyounghyun/node-app:latest   ← 현재 이미지

{
    "app": "node-app",
    "version": "1.0.0",              ← 이 값을 기억
    "language": "Node.js",
    ...
}

NAME       SYNC STATUS   HEALTH STATUS
node-app   Synced        Healthy
```

✅ **확인**: 현재 버전 기록, ArgoCD `Synced + Healthy` 상태

---

### Step 2: 코드 변경 — node-app 응답 수정

API 응답에 새 필드를 추가해 배포 변경을 시뮬레이션합니다.

```bash
cd ~/workspace/git-argocd-lecture/app/node-app
```

**app.js의 `GET /` 응답에 `description` 필드 추가**

```bash
# 변경 전 현재 GET / 응답 확인
grep -A 10 "app.get\('/'," src/app.js
```

```bash
# src/app.js 수정 — GET / 응답에 description 필드 추가
# 아래 내용을 직접 편집기로 수정하거나 sed 사용

# 변경 방법 (편집기 사용 권장):
# app.js의 res.status(200).json({...}) 블록에 아래 한 줄 추가:
#   description: 'GitOps Demo v2',
```

```bash
# sed로 자동 수정 (environment 필드 바로 다음에 description 추가)
sed -i '' 's/    environment: APP_ENV,/    environment: APP_ENV,\n    description: '"'"'GitOps Demo v2'"'"',/' \
  src/app.js

# 수정 확인
grep -A 12 "app.get\('/'," src/app.js
```

```
예상 수정 결과:
app.get('/', (req, res) => {
  res.status(200).json({
    app:       APP_NAME,
    version:   APP_VERSION,
    language:  'Node.js',
    framework: 'Express',
    port:      PORT,
    environment: APP_ENV,
    description: 'GitOps Demo v2',   ← 새로 추가된 필드
  });
});
```

**테스트 파일도 업데이트**

```bash
# test/app.test.js에서 description 필드 검증 추가 (선택)
# 또는 기존 테스트가 통과하는지만 확인
npm test
```

```
예상 출력:
ℹ tests 4
ℹ pass 4
ℹ fail 0   ← 기존 테스트 그대로 통과
```

✅ **확인**: 코드 수정 완료, 로컬 테스트 통과

---

### Step 3: commit + push → CI 트리거

```bash
cd ~/workspace/git-argocd-lecture

git add app/node-app/src/app.js
git commit -m "feat(node-app): add description field to GET / response"
git push origin main
```

```
예상 출력:
[main abc1234] feat(node-app): add description field to GET / response
To https://github.com/<username>/git-argocd-lecture.git
   xyz..abc  main -> main
```

```bash
# CI 실행 확인 (브라우저)
open https://github.com/$GITHUB_USERNAME/git-argocd-lecture/actions
```

```
예상 화면:
CI - Node.js App   ● Running   (방금 push로 트리거)
```

✅ **확인**: `CI - Node.js App` 워크플로우 트리거, Java/Python CI는 미트리거

---

### Step 4: CI 완료 확인 + 새 이미지 SHA 확인

```bash
# CI 완료 대기 (약 3~5분)
# Actions 탭에서 ✅ 표시 확인

# 완료 후 새 이미지 SHA 확인
open https://github.com/$GITHUB_USERNAME?tab=packages
```

```
예상 화면:
node-app
  sha-abc1234567890abcdef...  Published x minutes ago  ← 새 이미지
  latest                      Published x minutes ago
```

```bash
# 새 SHA 태그를 로컬에서 확인 (git log로 커밋 SHA 확인)
NEW_SHA=$(git rev-parse HEAD)
echo "Full SHA: $NEW_SHA"
echo "Image tag: ghcr.io/$GITHUB_USERNAME/node-app:$NEW_SHA"
```

```
예상 출력:
Full SHA: abc1234567890abcdef1234567890abcdef123456
Image tag: ghcr.io/username/node-app:abc1234567890abcdef1234567890abcdef123456
```

✅ **확인**: CI 두 Job 모두 `✅`, GHCR에 새 이미지 태그 존재

---

### Step 5: 매니페스트 이미지 태그 업데이트

GitOps의 핵심: **Git이 배포의 단일 진실 소스**입니다.  
새 이미지가 배포되려면 매니페스트의 이미지 태그를 업데이트해야 합니다.

```bash
cd ~/workspace/git-argocd-lecture

# 현재 이미지 태그 확인
grep "image:" manifests/node-app/deployment.yaml
```

```
예상 출력:
          image: ghcr.io/anbyounghyun/node-app:latest
```

```bash
# 이미지 태그를 새 SHA로 업데이트
# 방법 1: sed로 자동 업데이트
NEW_SHA=$(git rev-parse HEAD)
sed -i '' \
  "s|image: ghcr.io/anbyounghyun/node-app:.*|image: ghcr.io/$GITHUB_USERNAME/node-app:$NEW_SHA|" \
  manifests/node-app/deployment.yaml

# APP_VERSION 환경변수도 업데이트
SHORT_SHA=$(git rev-parse --short HEAD)
sed -i '' \
  "s|value: \"PLACEHOLDER_SHA\"|value: \"$SHORT_SHA\"|" \
  manifests/node-app/deployment.yaml

# 변경 확인
grep -A 2 "image:\|APP_VERSION" manifests/node-app/deployment.yaml
```

```
예상 출력:
          image: ghcr.io/username/node-app:abc1234...   ← 새 SHA
            - name: APP_VERSION
              value: "abc1234"                           ← Short SHA
```

```bash
# 변경 커밋 + 푸시
git add manifests/node-app/deployment.yaml
git commit -m "deploy(node-app): update image to $SHORT_SHA"
git push origin main
```

```
예상 출력:
[main def5678] deploy(node-app): update image to abc1234
```

✅ **확인**: `manifests/node-app/deployment.yaml`에 새 이미지 SHA 반영 후 push 완료

---

### Step 6: ArgoCD 자동 동기화 확인

ArgoCD는 3분 주기로 Git을 폴링합니다. 즉시 확인하려면 수동 Sync를 트리거할 수 있습니다.

**방법 A: 기다리기 (자동 감지 - 최대 3분)**

```bash
# ArgoCD 상태 실시간 모니터링
watch kubectl get applications -n argocd
```

```
예상 상태 전환:
node-app   Synced    Healthy   → OutOfSync 감지
node-app   OutOfSync Healthy   → 동기화 시작
node-app   Syncing   Healthy   → 파드 교체 중
node-app   Synced    Healthy   ✅ 완료
```

**방법 B: 수동 Sync 트리거**

```bash
# ArgoCD CLI가 설치된 경우
argocd app sync node-app

# 또는 kubectl로 annotation 추가
kubectl annotate application node-app -n argocd \
  argocd.argoproj.io/refresh="hard" --overwrite
```

**ArgoCD UI에서 확인**

```
ArgoCD UI → node-app 앱 클릭
→ SYNC STATUS: OutOfSync (잠시) → Synced ✅
→ HEALTH STATUS: Degraded (파드 교체 중) → Healthy ✅
```

```bash
# 파드 교체 과정 모니터링 (새 파드 기동 + 구 파드 종료)
kubectl get pods -n apps -l app=node-app -w
```

```
예상 출력 (RollingUpdate):
NAME                       READY   STATUS    RESTARTS
node-app-abc-xxx           1/1     Running   0         ← 구 파드
node-app-def-yyy           0/1     Pending   0         ← 신규 파드 기동
node-app-def-yyy           0/1     Running   0
node-app-def-yyy           1/1     Running   0         ← readinessProbe 통과
node-app-abc-xxx           1/1     Terminating 0       ← 구 파드 종료
node-app-abc-xxx           0/1     Terminating 0
(구 파드 완전 삭제)
```

✅ **확인**: ArgoCD `OutOfSync` → `Synced` 자동 전환, 파드 RollingUpdate 관찰

---

### Step 7: 새 버전 배포 검증

```bash
# Ctrl+C로 watch 종료 후

# 배포된 이미지 버전 확인
kubectl get deployment node-app -n apps \
  -o jsonpath='{.spec.template.spec.containers[0].image}' && echo ""

# 새 버전 API 응답 확인
curl -s http://apps.local/node | python3 -m json.tool
curl -s http://apps.local/node/health | python3 -m json.tool
```

```
예상 출력 (GET /):
{
    "app": "node-app",
    "version": "abc1234",           ← 새 SHA 버전
    "language": "Node.js",
    "framework": "Express",
    "port": 3000,
    "environment": "production",
    "description": "GitOps Demo v2" ← 새로 추가된 필드!
}

예상 출력 (GET /health):
{
    "status": "ok",
    "app": "node-app",
    "version": "abc1234"
}
```

✅ **확인**: `description` 필드가 응답에 포함, version에 새 SHA 반영

---

### Step 8: Self-healing 테스트

ArgoCD의 `selfHeal: true` 설정으로 kubectl 직접 수정이 자동 복원되는지 확인합니다.

**8-1. kubectl로 직접 수정 (Self-healing 유발)**

```bash
# 현재 replicas 확인 (1개)
kubectl get deployment node-app -n apps -o jsonpath='{.spec.replicas}' && echo ""

# replicas를 5로 직접 변경
kubectl scale deployment node-app -n apps --replicas=5

# 즉시 파드 수 증가 확인
kubectl get pods -n apps -l app=node-app
```

```
예상 출력:
1  (현재 replica)

node-app-xxx-aaa   1/1   Running   (기존)
node-app-xxx-bbb   1/1   Running   (새로 추가)
node-app-xxx-ccc   0/1   Running   (새로 추가)
node-app-xxx-ddd   0/1   Pending   (새로 추가)
node-app-xxx-eee   0/1   Pending   (새로 추가)
```

**8-2. ArgoCD Self-healing 동작 관찰**

```bash
# ArgoCD 상태 실시간 확인 (30초 이내 OutOfSync 감지)
watch kubectl get applications -n argocd

# 별도 터미널: 파드 수 변화 모니터링
watch kubectl get pods -n apps -l app=node-app
```

```
예상 상태 전환 (30초 이내):
node-app   Synced    Healthy  → OutOfSync 감지
node-app   Syncing   Healthy  → selfHeal 실행
node-app   Synced    Healthy  ✅ replicas=1로 복원
```

```bash
# 복원 확인
kubectl get deployment node-app -n apps \
  -o jsonpath='{.spec.replicas}' && echo ""
```

```
예상 출력:
1   ← Git에 정의된 원래 값으로 복원됨
```

✅ **확인**: kubectl로 변경한 replicas=5가 30초 이내 replicas=1로 자동 복원

---

### Step 9: 롤백 테스트

#### 방법 A: Git revert (권장 — GitOps 원칙 준수)

```bash
cd ~/workspace/git-argocd-lecture

# 최근 커밋 이력 확인
git log --oneline manifests/node-app/ | head -5
```

```
예상 출력:
def5678 deploy(node-app): update image to abc1234  ← 현재 (되돌릴 커밋)
xyz9012 initial commit: add node-app manifests
```

```bash
# 가장 최근 커밋(이미지 업데이트) revert
git revert HEAD --no-edit

# revert 커밋 확인
git log --oneline manifests/node-app/ | head -3
```

```
예상 출력:
ghi3456 Revert "deploy(node-app): update image to abc1234"
def5678 deploy(node-app): update image to abc1234
xyz9012 initial commit: add node-app manifests
```

```bash
# revert 커밋 push
git push origin main
```

```
예상 결과:
ArgoCD가 revert 커밋을 감지 → 이전 이미지로 자동 배포
```

```bash
# ArgoCD 동기화 후 이전 버전 확인
sleep 60  # ArgoCD 감지 대기 (또는 수동 Sync)
curl -s http://apps.local/node | python3 -m json.tool
```

```
예상 출력:
{
    "app": "node-app",
    "version": "1.0.0",     ← 이전 버전으로 복원
    "language": "Node.js",
    ...
    (description 필드 없음) ← revert로 이전 코드로 돌아감
}
```

#### 방법 B: ArgoCD UI 롤백

```
ArgoCD UI → node-app 앱 클릭
→ HISTORY AND ROLLBACK (시계 아이콘)
→ 이전 Revision (초록색) 선택
→ ROLLBACK 클릭 → 확인
```

> ⚠️ **주의**: UI 롤백은 ArgoCD 내부 히스토리 기준입니다.  
> Git 상태와 다를 수 있으므로 이후 Git revert로 일관성을 맞추는 것을 권장합니다.

✅ **확인**: 이전 버전 이미지로 자동 배포, `description` 필드 없는 응답 확인

---

### Step 10: 전체 E2E 흐름 복기

지금까지 실습한 전체 흐름을 다이어그램으로 복습합니다.

```
[완료한 실습 흐름]

Step 2  코드 수정 (app.js - description 추가)
  │
Step 3  git push origin main
  │
Step 4  GitHub Actions CI 자동 실행
  │       build-and-test ✅ → docker-build-push ✅
  │       GHCR에 새 이미지 등록
  │
Step 5  manifests/ 이미지 태그 업데이트 + push
  │
Step 6  ArgoCD 자동 감지 → OutOfSync → Syncing → Synced
  │
Step 7  curl apps.local/node → 새 버전 응답 확인
  │
Step 8  Self-healing: kubectl scale → ArgoCD 자동 복원
  │
Step 9  Git revert → 이전 버전으로 롤백
```

```bash
# 최종 상태 확인
kubectl get pods -n apps
kubectl get applications -n argocd
curl -s http://apps.local/java/health && \
curl -s http://apps.local/node/health && \
curl -s http://apps.local/python/health
```

```
예상 출력:
NAME                   READY   STATUS    RESTARTS
java-app-xxx           1/1     Running   0
node-app-xxx           1/1     Running   0
python-app-xxx         1/1     Running   0

NAME         SYNC STATUS   HEALTH STATUS
java-app     Synced        Healthy
node-app     Synced        Healthy
python-app   Synced        Healthy

{"status":"ok","app":"java-app","version":"..."}
{"status":"ok","app":"node-app","version":"..."}
{"status":"ok","app":"python-app","version":"..."}
```

✅ **확인**: 3개 앱 전체 정상, E2E 파이프라인 완전 동작 확인

---

## ✅ 확인 체크리스트

- [ ] Step3: 코드 수정 후 `git push` → `CI - Node.js App` 자동 트리거 확인
- [ ] Step4: `Build & Test` ✅ + `Docker Build & Push` ✅ 두 Job 모두 성공
- [ ] Step4: GHCR에 새 SHA 태그 이미지 등록 확인
- [ ] Step5: `manifests/node-app/deployment.yaml` 이미지 태그 SHA로 업데이트 + push
- [ ] Step6: ArgoCD `OutOfSync` → `Synced` 자동 전환 확인 (3분 이내)
- [ ] Step6: 파드 RollingUpdate 관찰 (무중단 — 구 파드 종료 전 신규 파드 Ready)
- [ ] Step7: `curl http://apps.local/node` — `description` 필드 포함 새 응답 확인
- [ ] Step8: `kubectl scale replicas=5` 후 ArgoCD Self-healing으로 30초 내 `replicas=1` 복원
- [ ] Step9: `git revert` + push → ArgoCD가 이전 이미지로 자동 롤백 확인
- [ ] Step10: 전체 E2E 흐름 설명 가능 (코드 변경 → 배포 → Self-healing → 롤백)

---

## 🎓 핵심 정리

| GitOps 개념 | 이번 실습에서 확인한 내용 |
|------------|----------------------|
| **Single Source of Truth** | `manifests/`의 이미지 태그가 배포 상태를 결정 |
| **Pull-based 배포** | ArgoCD가 주기적으로 Git을 폴링해 변경 감지 |
| **Self-healing** | kubectl 직접 수정 → ArgoCD가 Git 상태로 자동 복원 |
| **감사 추적** | 모든 배포 이력이 Git 커밋으로 기록됨 |
| **롤백 = Git revert** | 코드 되돌리기 → 자동 이전 버전 배포 |

---

**다음 단계**: [09. AWS EKS 전환](09-aws-eks.md)
