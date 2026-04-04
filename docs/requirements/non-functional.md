# 비기능 요구사항 (Non-Functional Requirements)

> 문서 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 작성: docs 에이전트

---

## 1. 이미지 태그 관리

| ID | 요구사항 | 기준값 |
|----|---------|-------|
| NFR-TAG-001 | 컨테이너 이미지 태그는 **git SHA** (short: 7자리 이상) 기반으로 생성 | `ghcr.io/<org>/<app>:<git-sha>` |
| NFR-TAG-002 | `main` 브랜치 push 시 `latest` 태그 추가 병행 | `ghcr.io/<org>/<app>:latest` |
| NFR-TAG-003 | 태그 형식: `<git-sha>` (예: `a1b2c3d`) | 7자리 hex |
| NFR-TAG-004 | 동일 SHA에 대한 재빌드 시 이미지 덮어쓰기 허용 | — |

---

## 2. CI 파이프라인 성능 목표

| ID | 요구사항 | 목표값 |
|----|---------|-------|
| NFR-CI-001 | Java 앱 전체 CI 실행 시간 (빌드 + 테스트 + 이미지 푸시) | **10분 이내** |
| NFR-CI-002 | Node.js 앱 전체 CI 실행 시간 | **5분 이내** |
| NFR-CI-003 | Python 앱 전체 CI 실행 시간 | **5분 이내** |
| NFR-CI-004 | Docker 레이어 캐시 활용으로 재빌드 시간 단축 | 최초 빌드의 50% 이내 |
| NFR-CI-005 | 테스트 실패 시 즉시 파이프라인 종료 (fail-fast) | — |

---

## 3. ArgoCD 동기화

| ID | 요구사항 | 기준값 |
|----|---------|-------|
| NFR-ARGO-001 | Git 저장소 폴링 주기 (auto-sync interval) | **3분** |
| NFR-ARGO-002 | 동기화 완료까지 최대 허용 시간 | **5분** |
| NFR-ARGO-003 | Self-healing: 수동 변경 감지 후 Git 상태 복원 대기 시간 | **5분** |
| NFR-ARGO-004 | 동기화 실패 시 ArgoCD UI에 오류 메시지 표시 | — |
| NFR-ARGO-005 | Rollback 지원: 이전 revision으로 수동 롤백 가능 | — |

---

## 4. K8s 리소스 제한 (requests / limits)

> 모든 Deployment에 `resources.requests` 및 `resources.limits`를 반드시 설정한다.

### 4.1 기본 기준값 (샘플 앱 기준)

| 앱 | CPU requests | CPU limits | Memory requests | Memory limits |
|----|-------------|-----------|----------------|--------------|
| java-app | `250m` | `500m` | `256Mi` | `512Mi` |
| node-app | `100m` | `200m` | `128Mi` | `256Mi` |
| python-app | `100m` | `200m` | `128Mi` | `256Mi` |

### 4.2 리소스 설정 규칙

| ID | 요구사항 |
|----|---------|
| NFR-RES-001 | 모든 컨테이너에 `requests`와 `limits` 모두 설정 필수 |
| NFR-RES-002 | `limits`는 `requests`의 최대 2배 이내로 설정 |
| NFR-RES-003 | QoS 클래스: Burstable (requests < limits) |
| NFR-RES-004 | Java 앱은 JVM heap 크기를 컨테이너 메모리 limits의 75% 이내로 설정 |

---

## 5. 가용성 및 안정성

| ID | 요구사항 | 기준값 |
|----|---------|-------|
| NFR-HA-001 | 각 앱 최소 replica 수 | **1** (로컬), **2** (EKS) |
| NFR-HA-002 | liveness probe 실패 허용 횟수 | 3회 |
| NFR-HA-003 | readiness probe 초기 지연 | Java: 30초, Node/Python: 10초 |
| NFR-HA-004 | 롤링 업데이트 전략 사용 (`RollingUpdate`) | — |

---

## 6. 보안

| ID | 요구사항 |
|----|---------|
| NFR-SEC-001 | 이미지 레지스트리 인증 정보는 GitHub Actions Secret으로 관리 |
| NFR-SEC-002 | K8s Secret 사용 시 plaintext 저장 금지 |
| NFR-SEC-003 | Dockerfile에서 root 사용자 실행 지양 (non-root user 권장) |
| NFR-SEC-004 | 이미지 base는 공식 slim/alpine 이미지 사용 |

---

## 7. 이식성

| ID | 요구사항 |
|----|---------|
| NFR-PORT-001 | 로컬(k3s)과 AWS EKS에서 동일한 매니페스트 사용 가능하도록 환경 의존성 최소화 |
| NFR-PORT-002 | 환경별 차이는 Ingress class 또는 StorageClass 등 최소 항목으로 한정 |
| NFR-PORT-003 | 환경 변수로 설정 주입 (`ConfigMap` 활용) |
