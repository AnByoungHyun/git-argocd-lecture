# 유즈케이스: CD 파이프라인 (ArgoCD GitOps)

> 문서 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 작성: docs 에이전트  
> 관련 요구사항: [기능 요구사항 §3](../requirements/functional.md), [비기능 요구사항 §3](../requirements/non-functional.md)

---

## 개요

ArgoCD 기반 GitOps CD 파이프라인의 주요 유즈케이스를 정의한다.  
Git 저장소를 단일 진실의 원천(Single Source of Truth)으로 삼아, 모든 배포 변경은 Git을 통해서만 발생한다.

### 액터 및 시스템

| 구분 | 설명 |
|------|------|
| **주 액터** | GitHub Actions (CI 자동화), ArgoCD (CD 자동화), 운영자 |
| **시스템** | GitHub, ArgoCD, Kubernetes (k3s / EKS) |
| **GitOps 원칙** | 클러스터 직접 변경 금지 — 반드시 Git → ArgoCD 경로로만 배포 |

---

## UC-CD-001: 이미지 태그 업데이트 → ArgoCD 자동 동기화 → K8s 배포

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-CD-001 |
| **이름** | GitOps 자동 배포 흐름 |
| **액터** | GitHub Actions (트리거), ArgoCD (자동 실행) |
| **트리거** | CI 파이프라인(UC-CI-001) 완료 후 `manifests/` 이미지 태그 업데이트 커밋 |
| **우선순위** | 필수 |

### 사전조건

- ArgoCD가 클러스터에 설치되어 있고 정상 동작 중
- ArgoCD Application CR이 해당 앱에 대해 설정됨
- ArgoCD가 Git 저장소의 `manifests/<app>/` 경로를 감시 중
- auto-sync 활성화 상태

### 주요 흐름

```
1. CI 파이프라인(UC-CI-001)에서 새 이미지 빌드 완료
   → 이미지: ghcr.io/<org>/java-app:a1b2c3d

2. CI가 manifests/java-app/deployment.yaml 이미지 태그 업데이트
   변경 전: image: ghcr.io/<org>/java-app:9z8y7x6
   변경 후: image: ghcr.io/<org>/java-app:a1b2c3d

3. CI가 변경 내용을 main 브랜치에 커밋 & push
   커밋 메시지: "ci: update java-app image to a1b2c3d"

4. ArgoCD가 Git 저장소 폴링 (기본 3분 주기)
   → 새 커밋 감지: manifests/java-app/deployment.yaml 변경됨

5. ArgoCD가 현재 클러스터 상태와 Git 상태 비교 (diff)
   → Out of Sync 상태 감지

6. auto-sync 활성화 → ArgoCD가 자동으로 동기화 시작
   → kubectl apply -f manifests/java-app/ 동등 작업 수행

7. K8s가 새 Deployment 롤아웃 시작
   → RollingUpdate 전략: 기존 Pod 유지하면서 새 Pod 순차 생성
   → readiness probe 통과 후 트래픽 전환

8. 모든 Pod가 새 이미지로 교체 완료
   → ArgoCD 상태: Synced ✅

9. ArgoCD UI에서 배포 완료 상태 확인 가능
```

### 사후조건 (성공 시)

- 클러스터의 `java-app` Pod가 새 이미지(`a1b2c3d`)로 실행 중
- ArgoCD Application 상태: `Synced`, `Healthy`
- 기존 Pod는 새 Pod 정상 기동 후 종료 (무중단 배포)

### 사후조건 (실패 시)

- ArgoCD Application 상태: `Degraded` 또는 `OutOfSync`
- 이전 Pod는 유지 (RollingUpdate로 인해 일부 실패 시 중단)
- → UC-CD-003 (롤백) 시나리오로 이어짐

### 데이터 흐름 다이어그램

```
[Developer]
    │ git push (코드 변경)
    ▼
[GitHub Actions CI]
    │ 1. 빌드/테스트
    │ 2. Docker 이미지 빌드 & GHCR 푸시
    │ 3. manifests/deployment.yaml 이미지 태그 업데이트 커밋
    ▼
[GitHub Repository]  ←── ArgoCD 감시 (3분 주기 폴링)
    │
    ▼
[ArgoCD]
    │ Out of Sync 감지 → auto-sync 실행
    ▼
[Kubernetes Cluster]
    │ RollingUpdate Deployment
    ▼
[새 버전 앱 Pod 실행 완료]
```

---

## UC-CD-002: 수동 클러스터 변경 → Self-healing으로 Git 상태 복원

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-CD-002 |
| **이름** | ArgoCD Self-healing (Git 상태 자동 복원) |
| **액터** | 운영자 (의도치 않은 직접 변경), ArgoCD (자동 복원) |
| **트리거** | kubectl 등으로 클러스터 리소스를 직접 수정 |
| **우선순위** | 권장 |

### 사전조건

- ArgoCD Application CR에 `selfHeal: true` 설정
- ArgoCD가 해당 namespace의 리소스를 관리 중
- Git 저장소의 `manifests/` 상태가 최신

### 주요 흐름

```
1. 운영자가 디버깅/테스트 목적으로 클러스터 리소스 직접 수정
   예시:
   $ kubectl set image deployment/java-app java-app=ghcr.io/<org>/java-app:test-build
   또는
   $ kubectl scale deployment/java-app --replicas=5

2. ArgoCD가 클러스터 상태와 Git 상태 비교 (실시간 감시)
   → Out of Sync 감지
   → 불일치 항목: image 태그 또는 replicas 수

3. selfHeal: true 설정에 따라 ArgoCD가 자동 복원 결정
   → 복원 대기 시간 없이 즉시 동기화 실행 (기본값)

4. ArgoCD가 Git 상태로 리소스 복원
   → kubectl apply로 Git의 매니페스트 재적용
   → image: 원래 SHA 태그로 복원
   → replicas: Git에 정의된 값으로 복원

5. ArgoCD UI에 이벤트 기록:
   "self healed from manual change"

6. ArgoCD Application 상태: Synced ✅
```

### 사후조건

- 클러스터 리소스가 Git 상태와 일치하도록 복원됨
- 수동 변경 내용이 ArgoCD에 의해 덮어쓰여짐
- ArgoCD 이벤트 로그에 self-healing 기록 남음

### 주의사항

| 항목 | 설명 |
|------|------|
| **긴급 패치가 필요한 경우** | 직접 변경 대신 → Git commit → ArgoCD sync 경로로 처리 |
| **임시 스케일 조정** | Git의 replicas 값을 변경 후 커밋해야 유지됨 |
| **self-heal 비활성화 시** | ArgoCD는 Out of Sync 표시만 하고 자동 복원 안 함 |

### ArgoCD Application CR 설정 예시

```yaml
spec:
  syncPolicy:
    automated:
      prune: true       # Git에서 삭제된 리소스 클러스터에서도 삭제
      selfHeal: true    # 수동 변경 감지 시 자동 복원
```

---

## UC-CD-003: 배포 실패 → 이전 revision으로 롤백

### 기본 정보

| 항목 | 내용 |
|------|------|
| **ID** | UC-CD-003 |
| **이름** | ArgoCD 롤백 (이전 revision 복원) |
| **액터** | 운영자 |
| **트리거** | 신규 배포 후 앱 비정상 (Degraded), 장애 감지 |
| **우선순위** | 필수 |

### 사전조건

- ArgoCD에 이전 배포 history가 존재 (최소 1개 이상)
- ArgoCD UI 또는 CLI 접근 권한 보유
- ArgoCD Application 상태가 `Degraded` 또는 앱 오동작 확인됨

### 주요 흐름

#### 방법 A: ArgoCD UI를 통한 롤백

```
1. 장애 감지
   - liveness probe 실패로 Pod CrashLoopBackOff
   - 또는 서비스 응답 오류 (5xx)
   - 또는 ArgoCD Application 상태: Degraded

2. ArgoCD UI 접속 (http://argocd.local 또는 포트포워딩)

3. 해당 Application 선택 → [History and Rollback] 탭 클릭

4. 이전 정상 배포 Revision 확인
   예시:
   Revision 5: a1b2c3d (현재, Degraded)
   Revision 4: 9z8y7x6 (이전, Healthy) ← 이 버전으로 롤백

5. Revision 4 선택 → [Rollback] 버튼 클릭

6. ArgoCD가 Revision 4의 매니페스트로 클러스터 상태 복원
   → kubectl apply (이전 이미지 태그로 Deployment 업데이트)

7. K8s RollingUpdate로 Pod 교체
   → 이전 이미지로 Pod 재생성
   → readiness probe 통과 확인

8. ArgoCD Application 상태: Synced, Healthy ✅
```

#### 방법 B: argocd CLI를 통한 롤백

```bash
# ArgoCD CLI 설치 후
argocd login <argocd-server>

# 히스토리 확인
argocd app history java-app

# 특정 revision으로 롤백
argocd app rollback java-app <revision-number>

# 상태 확인
argocd app get java-app
```

#### 방법 C: Git revert를 통한 롤백 (권장)

```
1. 문제가 된 커밋 확인
   $ git log --oneline manifests/java-app/

2. 해당 커밋 revert
   $ git revert <commit-sha>
   $ git push origin main

3. ArgoCD가 revert 커밋 감지 → 자동 동기화
   → 이전 이미지 태그로 자동 배포
```

> **권장**: Git revert 방법을 우선 사용한다 (GitOps 원칙 준수, 감사 추적 보장).  
> ArgoCD UI 롤백은 Git 히스토리에 반영되지 않으므로 긴급 상황에서만 사용한다.

### 사후조건

- 이전 정상 버전 이미지로 Pod 실행 중
- ArgoCD Application 상태: Synced, Healthy
- 운영자가 장애 원인 분석 후 수정 버전 재배포

### 사후 처리 흐름

```
롤백 완료
    │
    ├─→ 장애 원인 분석 (로그, 이미지 변경 내용 검토)
    │
    ├─→ 코드 수정 → 테스트 → PR → main push
    │
    └─→ CI/CD 파이프라인으로 정상 버전 재배포
```

---

## 유즈케이스 관계도

```
[CI 완료 (UC-CI-001)]
    │
    │ manifests/ 이미지 태그 업데이트 커밋
    ▼
UC-CD-001: 자동 동기화 & 배포
    │
    ├─→ 성공: Synced ✅ Healthy ✅
    │
    └─→ 실패: Degraded ❌
              │
              └─→ UC-CD-003: 롤백
                      │
                      ├─→ ArgoCD UI 롤백 (즉각, Git 미반영)
                      └─→ Git revert (권장, 감사 추적)

[운영자 직접 수정 (kubectl)]
    │
    ▼
UC-CD-002: Self-healing (자동 복원)
    │
    └─→ Git 상태로 복원 ✅
```
