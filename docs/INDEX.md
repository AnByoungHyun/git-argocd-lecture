# 프로젝트 문서 인덱스

> 이 파일은 모든 에이전트가 작업 전 참조하는 문서 맵입니다.  
> docs 에이전트가 문서를 추가/수정할 때마다 이 인덱스를 갱신합니다.  
> 최종 수정: 2026-04-04 (Phase 4 최종 완료)

---

## 공통 (모든 에이전트 참조)

| 문서 | 설명 | 상태 |
|------|------|------|
| [기능 요구사항](requirements/functional.md) | 멀티 언어 앱, CI/CD, K8s 기능 목록과 우선순위 | ✅ 완료 🔒 |
| [비기능 요구사항](requirements/non-functional.md) | 성능, 이미지 태그, ArgoCD 동기화, 리소스 제한 | ✅ 완료 🔒 |
| [제약조건](requirements/constraints.md) | 기술 스택, 외부 의존성, 금지 사항, 전제조건 | ✅ 완료 🔒 |

> 🔒 `requirements/` 디렉토리는 잠금 상태입니다. 수정 불가.

---

## 가이드 (단계별 재현 가이드)

> 상태 범례: 🏗️ 골격 생성 완료 | ✏️ 이론/실습 작성 중 | ✅ 완료  
> **Phase 4 완료 기준: 모든 가이드 ✅ 완료 (Mermaid 다이어그램 포함)**

| 번호 | 문서 | 설명 | 상태 |
|------|------|------|------|
| 00 | [전체 과정 소개](guides/00-overview.md) | 아키텍처, 기술 스택, 학습 흐름, 사전 지식 | ✅ 완료 |
| 01 | [사전 준비](guides/01-prerequisites.md) | 도구 설치, 버전 확인, GitHub 저장소 준비 | ✅ 완료 |
| 02 | [샘플 앱 구조 이해](guides/02-sample-apps.md) | 3개 앱 코드 분석, 로컬 실행, API 응답 확인 | ✅ 완료 |
| 03 | [Dockerfile + 이미지 빌드](guides/03-dockerize.md) | 멀티스테이지 빌드 분석, 로컬 빌드, GHCR 푸시 | ✅ 완료 |
| 04 | [GitHub Actions CI](guides/04-github-actions.md) | 워크플로우 구조, 트리거, 시크릿, 실행 확인 | ✅ 완료 |
| 05 | [K8s 매니페스트](guides/05-k8s-manifests.md) | Deployment/Service/Ingress 분석, 리소스 제한, probe | ✅ 완료 |
| 06 | [Rancher Desktop + ArgoCD](guides/06-rancher-argocd.md) | 클러스터 구성, Ingress Controller, ArgoCD 설치/설정 | ✅ 완료 |
| 07 | [GitOps 배포 실습](guides/07-gitops-deploy.md) | 전체 파이프라인 E2E 체험, self-healing, 롤백 | ✅ 완료 |
| 09 | [(심화) AWS EKS 전환](guides/09-aws-eks.md) | EKS 클러스터 생성, 매니페스트 수정, 전환 검증 | ✅ 완료 |

---

## 유즈케이스

| 문서 | 설명 | 상태 |
|------|------|------|
| [CI 파이프라인](usecases/ci-pipeline.md) | main push / PR / 빌드 실패 시 CI 흐름 (UC-CI-001~003) | ✅ 완료 🔒 |
| [CD 파이프라인](usecases/cd-pipeline.md) | 자동 배포 / Self-healing / 롤백 (UC-CD-001~003) | ✅ 완료 🔒 |
| [환경 전환](usecases/env-transition.md) | 로컬 k3s → AWS EKS 전환 절차 및 변경 항목 (UC-ENV-001) | ✅ 완료 🔒 |

> 🔒 `usecases/` 디렉토리는 잠금 상태입니다. 수정 불가.

---

## 데이터 계층

| 문서 | 설명 | 상태 |
|------|------|------|
| _(해당 없음 — Stateless 샘플 앱, DB 미사용)_ | — | ➖ 불필요 |

---

## API 계층

| 문서 | 설명 | 상태 |
|------|------|------|
| [API 공통 명세](api/overview.md) | 3개 앱 공통 엔드포인트, 포트, Ingress 라우팅, 에러 형식 | ✅ 완료 |

---

## UI 계층

| 문서 | 설명 | 상태 |
|------|------|------|
| _(해당 없음 — API 전용 샘플 앱, UI 없음)_ | — | ➖ 불필요 |

---

## 의사결정 (ADR)

| 문서 | 설명 | 상태 |
|------|------|------|
| _(설계 결정 기록(ADR)이 여기에 추가됩니다)_ | — | 🔜 예정 |

---

## 진행 상황

| 문서 | 설명 | 상태 |
|------|------|------|
| [전체 진행 상황](progress/status.md) | 전체 작업 체크리스트, 에이전트별 완료 기록 | ✅ 최신 |

---

## 전체 Phase 현황 요약

| Phase | 범위 | 담당 | 상태 |
|-------|------|------|------|
| Phase 1 | 선행 문서화 (requirements/usecases/api) | docs | ✅ 완료 |
| Phase 2-1 | 샘플 앱 개발 (java/node/python) | app | ✅ 완료 |
| Phase 2-2 | CI 워크플로우 (GitHub Actions) | ci | ✅ 완료 |
| Phase 2-3 | K8s 매니페스트 + ArgoCD 설정 | cd | ✅ 완료 |
| **Phase 2 전체** | **앱 개발 + CI + CD 설정** | **app/ci/cd** | **✅ 완료** |
| Phase 3 | 인프라 구성 + 3개 앱 배포 확인 | infra | ✅ 최종 완료 |
| Phase 4 | 교육 가이드 완성 (9개 가이드, 15 Mermaid 블록) | docs | ✅ 완료 |
| Phase 5 | AWS EKS 전환 | infra | ⏳ 예정 (가이드 문서 준비 완료 → 09-aws-eks.md) |

---

## 운영 접근 정보 (Phase 3 완료 기준)

| 항목 | 값 |
|------|-----|
| ArgoCD UI | https://192.168.64.2:31853 |
| java-app | http://apps.local/java |
| node-app | http://apps.local/node |
| python-app | http://apps.local/python |
| GitHub 저장소 | https://github.com/AnByoungHyun/git-argocd-lecture |

> `/etc/hosts`에 `192.168.64.2 apps.local` 등록 필요
