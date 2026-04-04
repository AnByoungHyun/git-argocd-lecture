# 01. 사전 준비 — 실습 환경 구성

> 가이드 버전: 1.0.0  
> 최종 수정: 2026-04-04  
> 상태: ✅ 실습 단계 작성 완료  
> 다음 가이드: [02. 샘플 앱 구조 이해](02-sample-apps.md)

---

## 🎯 학습 목표

이 가이드를 완료하면 다음을 할 수 있습니다:

- [ ] 실습에 필요한 모든 도구를 설치하고 버전을 확인할 수 있다
- [ ] GitHub 계정과 저장소를 준비하고 로컬에 Clone할 수 있다
- [ ] Docker CLI와 kubectl이 정상 동작하는 것을 확인할 수 있다

---

## 📖 이론

### 실습 환경 개요

이 강의는 **로컬 PC(macOS)** 위에서 GitOps 전체 파이프라인을 경험하는 것을 목표로 합니다.

```
┌─────────────────────────────────────────────────────┐
│                   로컬 macOS 환경                    │
│                                                     │
│  ┌──────────────┐    ┌──────────────────────────┐  │
│  │  Git / GitHub│    │    Rancher Desktop        │  │
│  │  (소스 관리)  │    │  ┌────────┐ ┌─────────┐  │  │
│  └──────┬───────┘    │  │ k3s    │ │ Docker  │  │  │
│         │            │  │(K8s)   │ │  CLI    │  │  │
│         │            │  └────────┘ └─────────┘  │  │
│         │            └──────────────────────────┘  │
│         │                       ▲                  │
│         └───────────────────────┘                  │
│              kubectl / docker 명령                  │
└─────────────────────────────────────────────────────┘
```

### 도구별 역할

| 도구 | 역할 | 설치 방법 |
|------|------|---------|
| **Rancher Desktop** | 로컬 K8s 클러스터(k3s) + Docker CLI 환경 제공 | 직접 설치 |
| **kubectl** | K8s 클러스터 제어 CLI | Rancher Desktop에 포함 |
| **Docker CLI** | 컨테이너 이미지 빌드/실행 | Rancher Desktop에 포함 |
| **Git** | 소스코드 버전 관리 | Homebrew 또는 기본 제공 |
| **GitHub** | 원격 저장소 + GitHub Actions CI 트리거 | 계정 생성 필요 |

> 💡 **핵심**: Rancher Desktop 하나만 설치하면 kubectl과 Docker CLI가 함께 제공됩니다.

---

### Rancher Desktop이란? — k3s와 Kubernetes의 관계

#### Kubernetes(K8s)란?

**Kubernetes**는 컨테이너를 자동으로 배포·운영·확장해주는 오픈소스 플랫폼입니다.  
서버가 여러 대 있을 때 "어느 서버에서 몇 개의 컨테이너를 실행할지"를 자동으로 결정하고,  
컨테이너가 죽으면 자동으로 되살리며, 트래픽을 분산시켜주는 **운영체제 위의 운영체제**라고 생각할 수 있습니다.

```
Kubernetes가 해결하는 문제:
  ❌ 이전: "서버 3대에 수동으로 접속해서 각각 docker run..."
  ✅ 이후: "replicas: 3" 한 줄로 자동 배포 + 장애 시 자동 복구
```

#### k3s란?

**k3s**는 Rancher Labs(현 SUSE)가 만든 **경량 Kubernetes 배포판**입니다.  
표준 Kubernetes를 그대로 사용할 수 있지만, 설치 크기와 리소스 요구사항을 대폭 줄였습니다.

| 항목 | 표준 Kubernetes | k3s |
|------|---------------|-----|
| 설치 크기 | ~수백 MB (여러 컴포넌트) | ~70MB (단일 바이너리) |
| 최소 메모리 | 2GB+ | 512MB |
| 설치 방법 | 복잡한 다단계 설치 | `curl -sfL \| sh` 한 줄 |
| 프로덕션 호환성 | ✅ 완전 호환 | ✅ 완전 호환 (CNCF 인증) |
| 주요 용도 | 클라우드·온프레미스 운영 환경 | 로컬 개발, Edge, IoT |

> 💡 **k3s ≠ 간소화된 K8s**: k3s는 기능을 빼지 않고 **내부 구현을 최적화**했습니다.  
> 표준 `kubectl` 명령어가 그대로 동작하므로, 로컬에서 배운 내용이 AWS EKS에서도 동일하게 적용됩니다.

#### Rancher Desktop = k3s + Docker + GUI

**Rancher Desktop**은 macOS에서 k3s와 Docker를 **한 번에** 설치·관리해주는 데스크톱 애플리케이션입니다.

```
Rancher Desktop 포함 구성요소:
  ┌─────────────────────────────────┐
  │       Rancher Desktop           │
  │                                 │
  │  ┌──────────┐  ┌─────────────┐  │
  │  │  k3s     │  │  dockerd    │  │
  │  │ (K8s 클러스터) │  │ (컨테이너 런타임) │  │
  │  └──────────┘  └─────────────┘  │
  │                                 │
  │  + kubectl, nerdctl, helm CLI   │
  └─────────────────────────────────┘
         ↑ 이것들이 자동으로 PATH에 추가됨
```

이 강의에서 Rancher Desktop을 사용하는 이유:
- **단일 설치**로 K8s + Docker 환경 완성
- AWS EKS와 동일한 방식(`kubectl`)으로 클러스터 제어
- macOS Apple Silicon(M1/M2/M3) 및 Intel 모두 지원

---

### kubectl — K8s와 대화하는 CLI

**kubectl**("큐브시티엘" 또는 "큐브컨트롤")은 Kubernetes 클러스터에 명령을 전달하는 CLI 도구입니다.

```
사용자 명령               클러스터 동작
─────────────────────────────────────────
kubectl get pods      →  현재 실행 중인 Pod 목록 조회
kubectl apply -f X.yaml → X.yaml에 정의된 리소스 생성/수정
kubectl delete pod P  →  Pod P 강제 삭제
kubectl logs P        →  Pod P의 로그 출력
```

kubectl은 **클러스터 주소와 인증 정보**를 `~/.kube/config` 파일에서 읽습니다.  
Rancher Desktop이 이 파일을 자동으로 설정해주므로 별도 설정 없이 바로 사용할 수 있습니다.

```bash
# 어떤 클러스터에 연결되어 있는지 확인
kubectl config current-context
# 출력 예: rancher-desktop

# 클러스터의 "노드"(서버) 목록 조회
kubectl get nodes
```

> 🔑 **핵심 개념**: kubectl은 로컬 k3s에서도, AWS EKS에서도 **동일한 명령어**를 사용합니다.  
> context만 바꾸면 어떤 클러스터든 제어할 수 있습니다.

---

### Docker — 컨테이너 런타임 vs 빌드 도구

Docker는 두 가지 역할을 합니다. 혼동하기 쉬우니 명확히 구분합니다.

#### 역할 1: 이미지 빌드 도구

`Dockerfile`을 읽어서 **애플리케이션 이미지**를 만드는 도구입니다.

```
Dockerfile (레시피)
      │
      ▼  docker build
컨테이너 이미지 (실행 가능한 패키지)
      │
      ▼  docker push
레지스트리 (이미지 저장소)
```

```bash
# 이미지 빌드
docker build -t my-app:1.0 ./app/java-app

# 빌드된 이미지 목록 확인
docker images
```

#### 역할 2: 컨테이너 런타임

이미지를 실제로 **실행**하는 엔진입니다.  
K8s(k3s)는 내부적으로 Docker(또는 containerd)를 런타임으로 사용합니다.

```
컨테이너 이미지
      │
      ▼  docker run (로컬 실행)
   컨테이너 (실행 중인 프로세스)

      │
      ▼  K8s가 내부적으로 실행 (Pod 형태)
   컨테이너 (K8s가 관리)
```

| 역할 | 명령어 | 사용 시점 |
|------|-------|---------|
| 빌드 | `docker build` | CI 파이프라인에서 이미지 생성 시 |
| 실행 (로컬) | `docker run` | 로컬 테스트 시 |
| 실행 (K8s) | `kubectl apply` | K8s 클러스터 배포 시 |
| 저장소 업로드 | `docker push` | CI에서 레지스트리에 이미지 저장 시 |

> 💡 **이 강의에서의 흐름**: `docker build` → `docker push` (CI가 담당) → K8s가 이미지를 자동으로 `pull` 하여 실행

---

### GHCR — 컨테이너 이미지 저장소

**GitHub Container Registry(GHCR)**는 GitHub이 제공하는 컨테이너 이미지 저장소입니다.

```
이미지 저장소의 역할:
  CI (GitHub Actions)     GHCR                K8s 클러스터
  ──────────────────   ──────────────      ──────────────────
  docker build
  docker push      →  이미지 저장  →  이미지 pull → Pod 실행
                   (ghcr.io/user/app:sha)
```

**이미지 명명 규칙** (이 강의 기준):

```
ghcr.io / anbyounghyun / java-app : a1b2c3d4...
   ↑            ↑            ↑          ↑
GHCR 호스트  GitHub 계정   앱 이름   git SHA 태그
```

**왜 `latest` 태그를 사용하지 않는가?**

```
❌ latest 태그의 문제:
   - 언제 빌드된 이미지인지 추적 불가
   - 롤백 시 어느 버전으로 돌아갈지 불명확
   - GitOps 원칙 위반 (Git 상태 ↔ 클러스터 상태 불일치 가능)

✅ git SHA 태그의 장점:
   - git log로 어떤 코드가 배포됐는지 즉시 확인
   - 특정 커밋으로 정확한 롤백 가능
   - ArgoCD가 변경사항을 정확히 감지
```

---

### CI/CD 파이프라인 전체 흐름

이 강의에서 구현하는 전체 파이프라인을 한눈에 봅니다.

```
개발자
  │
  │  git push (코드 변경)
  ▼
GitHub 저장소
  │
  │  변경 감지 → 자동 트리거
  ▼
┌─────────────────────────────────┐
│    GitHub Actions (CI)          │
│                                 │
│  1. 소스코드 빌드               │
│  2. 단위 테스트 실행            │  ← 실패하면 여기서 중단
│  3. Docker 이미지 빌드          │
│  4. GHCR에 이미지 푸시          │  ← ghcr.io/user/app:sha
│  5. manifests/ 이미지 태그 업데이트 커밋 │
└─────────────────────────────────┘
  │
  │  manifests/ 변경 push
  ▼
GitHub 저장소 (manifests/ 업데이트)
  │
  │  3분 주기 폴링 (변경 감지)
  ▼
┌─────────────────────────────────┐
│    ArgoCD (CD)                  │
│                                 │
│  1. Git 상태 vs 클러스터 비교   │
│  2. Out of Sync 감지            │
│  3. 자동 동기화 (auto-sync)     │
│  4. K8s에 새 Deployment 적용    │
└─────────────────────────────────┘
  │
  │  RollingUpdate
  ▼
K8s 클러스터 (새 버전 Pod 실행)
  │
  ▼
사용자 접근: http://apps.local/java (또는 실제 도메인)
```

**각 단계의 역할 요약:**

| 단계 | 도구 | 트리거 | 산출물 |
|------|------|--------|--------|
| 코드 관리 | Git + GitHub | 개발자 push | 버전 관리된 소스코드 |
| CI (빌드/테스트) | GitHub Actions | push 이벤트 | GHCR 이미지 + 태그 업데이트 |
| CD (배포) | ArgoCD | Git 변경 감지 | K8s 클러스터 최신 상태 유지 |
| 서비스 운영 | Kubernetes(k3s) | 자동 | 안정적인 Pod 실행 |

> 💡 **GitOps 핵심 원칙**: 클러스터 상태는 **반드시 Git을 통해서만** 변경됩니다.  
> `kubectl apply`로 직접 수정하지 않고, Git commit → ArgoCD 동기화 경로를 사용합니다.

---

### Git 기반 워크플로우 — Fork, Clone, upstream

이 강의는 Git 워크플로우를 실제 협업 방식으로 구성했습니다.

#### Fork란?

**Fork**는 다른 사람의 GitHub 저장소를 **내 계정으로 복사**하는 것입니다.

```
원본 저장소 (upstream)
  AnByoungHyun/git-argocd-lecture
          │
          │  Fork (GitHub 버튼)
          ▼
내 저장소 (origin)
  your-username/git-argocd-lecture
          │
          │  git clone
          ▼
  로컬 PC (~workspace/git-argocd-lecture)
```

Fork가 필요한 이유:
- GitHub Actions Secrets을 **내 저장소에** 등록해야 CI가 동작합니다
- 원본 저장소에 직접 push 권한이 없기 때문입니다
- PR(Pull Request)로 기여하거나 독립적으로 실습할 수 있습니다

#### Clone이란?

**Clone**은 GitHub의 원격 저장소를 **로컬 PC로 복사**하는 것입니다.

```bash
git clone https://github.com/your-username/git-argocd-lecture.git
```

Clone 후 생성되는 원격 저장소 연결:

| remote 이름 | 대상 저장소 | 용도 |
|-------------|-----------|------|
| `origin` | `your-username/git-argocd-lecture` | 내 작업 push/pull |
| `upstream` | `AnByoungHyun/git-argocd-lecture` | 원본 업데이트 수신 |

#### upstream 동기화

강의 내용이 업데이트되면 upstream에서 최신 변경사항을 받아올 수 있습니다:

```bash
# upstream 최신 내용 가져오기
git fetch upstream

# 내 main 브랜치에 병합
git checkout main
git merge upstream/main

# 내 GitHub 저장소에 반영
git push origin main
```

> 💡 **정리**: `upstream`(원본) → `origin`(내 GitHub) → `local`(내 PC) 의 3단계 구조입니다.  
> 실습 중 수정 사항은 `origin`(내 저장소)에 push하면 CI가 자동으로 트리거됩니다.

---

## 🛠️ 실습 단계

---

### Step 1: Rancher Desktop 설치

Rancher Desktop은 macOS에서 K8s(k3s) 클러스터와 Docker 환경을 손쉽게 구성해주는 도구입니다.  
이 실습의 **핵심 전제 조건**입니다.

**사전 조건**
- macOS 12 Monterey 이상 (Apple Silicon / Intel 모두 지원)
- 최소 8GB RAM, 20GB 여유 디스크

**1-1. 설치 파일 다운로드**

```bash
# Homebrew가 설치된 경우 (권장)
brew install --cask rancher

# 또는 공식 사이트에서 직접 다운로드
open https://rancherdesktop.io
```

**1-2. 최초 실행 시 설정**

설치 후 Rancher Desktop을 실행하면 초기 설정 화면이 나타납니다:

1. **Container Runtime**: `dockerd (moby)` 선택 ← **반드시 dockerd 선택**
2. **Kubernetes version**: 최신 안정 버전 유지 (예: `v1.28.x`)
3. **Enable Kubernetes**: 체크 ✅

> ⚠️ `containerd` 대신 **`dockerd (moby)`** 를 선택해야 `docker` 명령어가 동작합니다.

**1-3. 설치 완료 확인**

Rancher Desktop 우측 상단 아이콘이 초록색(✅)으로 바뀌면 준비 완료입니다.  
(첫 실행 시 이미지 다운로드로 5~10분 소요될 수 있습니다)

```bash
# Rancher Desktop이 자동으로 PATH 설정 — 터미널 새로 열거나 아래 실행
export PATH="$HOME/.rd/bin:$PATH"
```

```
예상 출력: (설정 후 터미널에서 명령어 실행 가능)
```

✅ **확인**: Rancher Desktop 트레이 아이콘이 초록색으로 표시되면 성공

---

### Step 2: 도구 버전 확인

모든 필수 도구가 올바르게 설치되었는지 버전을 한 번에 확인합니다.

```bash
# kubectl 버전 확인
kubectl version --client --short 2>/dev/null || kubectl version --client

# Docker 버전 확인
docker version --format 'Client: {{.Client.Version}} / Server: {{.Server.Version}}'

# Git 버전 확인
git --version

# Node.js 버전 확인 (로컬 실행용 — 선택사항)
node --version 2>/dev/null || echo "Node.js not installed (optional)"

# Java 버전 확인 (로컬 실행용 — 선택사항)
java --version 2>/dev/null || echo "Java not installed (optional)"

# Python 버전 확인 (로컬 실행용 — 선택사항)
python3 --version 2>/dev/null || echo "Python not installed (optional)"
```

```
예상 출력 (예시):
Client Version: v1.28.x
Server Version: v1.28.x

Client: 24.x.x / Server: 24.x.x

git version 2.x.x

v18.x.x        ← 로컬 앱 실행 시 필요
openjdk 17.x.x ← 로컬 앱 실행 시 필요
Python 3.11.x  ← 로컬 앱 실행 시 필요
```

> 💡 **참고**: Node.js / Java / Python은 **로컬 직접 실행(Step 02 가이드)** 시 필요합니다.  
> Docker만으로 진행할 경우 필수가 아닙니다. (가이드 03 참조)

✅ **확인**: `kubectl`, `docker`, `git` 세 가지 명령어가 모두 버전을 출력하면 성공

---

### Step 3: K8s 클러스터 동작 확인

Rancher Desktop이 제공하는 k3s 클러스터가 정상 동작하는지 확인합니다.

```bash
# 현재 클러스터 컨텍스트 확인
kubectl config current-context

# 노드 상태 확인
kubectl get nodes

# 기본 네임스페이스 Pod 확인
kubectl get pods -A | head -20
```

```
예상 출력:
rancher-desktop          ← 또는 lima-rancher-desktop

NAME                   STATUS   ROLES                  AGE   VERSION
lima-rancher-desktop   Ready    control-plane,master   5m    v1.28.x

NAMESPACE     NAME                                     READY   STATUS    ...
kube-system   coredns-xxx-xxx                         1/1     Running   ...
kube-system   local-path-provisioner-xxx              1/1     Running   ...
```

✅ **확인**: 노드 STATUS가 `Ready`이면 클러스터 정상 동작

---

### Step 4: GitHub 계정 및 Personal Access Token(PAT) 생성

CI 파이프라인에서 GHCR(GitHub Container Registry)에 이미지를 푸시하려면 PAT가 필요합니다.

**4-1. PAT 생성 절차**

1. GitHub → 우측 상단 프로필 → **Settings**
2. 좌측 하단 **Developer settings** → **Personal access tokens** → **Tokens (classic)**
3. **Generate new token (classic)** 클릭
4. 설정:
   - **Note**: `git-argocd-lecture` (임의 이름)
   - **Expiration**: 90 days (실습 기간에 맞게 설정)
   - **Scopes**: 아래 권한 체크
     - ✅ `write:packages` — GHCR 이미지 푸시
     - ✅ `read:packages` — GHCR 이미지 풀
     - ✅ `delete:packages` — GHCR 이미지 삭제 (선택)
5. **Generate token** 클릭 → **토큰 복사 후 안전한 곳에 저장**

> ⚠️ 토큰은 생성 시 한 번만 보입니다. 반드시 복사해두세요.

**4-2. 환경 변수 설정**

```bash
# PAT를 환경 변수로 설정 (현재 터미널 세션)
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
export GITHUB_USERNAME="your-github-username"

# 영구 설정을 원하면 ~/.zshrc 또는 ~/.bashrc에 추가
echo 'export GITHUB_TOKEN="ghp_xxxx..."' >> ~/.zshrc
echo 'export GITHUB_USERNAME="your-github-username"' >> ~/.zshrc
```

**4-3. GHCR 로그인 테스트**

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin
```

```
예상 출력:
WARNING! Your password will be stored unencrypted ...
Login Succeeded
```

✅ **확인**: `Login Succeeded` 출력 확인

---

### Step 5: 실습 저장소 Fork 및 Clone

**5-1. GitHub에서 저장소 Fork**

```bash
# 브라우저에서 아래 URL로 이동
open https://github.com/AnByoungHyun/git-argocd-lecture

# 우측 상단 "Fork" 버튼 클릭 → "Create fork"
# Fork된 저장소: https://github.com/<your-username>/git-argocd-lecture
```

> 💡 **Fork vs Clone**: Fork는 본인 계정에 저장소 사본을 만드는 것입니다.  
> GitHub Actions CI가 본인 계정 저장소에서 실행되어야 Secrets 설정이 가능합니다.

**5-2. 로컬에 Clone**

```bash
# 실습 디렉토리 준비
mkdir -p ~/workspace && cd ~/workspace

# Fork한 본인 저장소를 Clone (username 수정 필요)
git clone https://github.com/$GITHUB_USERNAME/git-argocd-lecture.git

# 디렉토리 진입
cd git-argocd-lecture

# 디렉토리 구조 확인
ls -la
```

```
예상 출력:
drwxr-xr-x  app/            ← 3개 샘플 앱
drwxr-xr-x  argocd/         ← ArgoCD Application CR
drwxr-xr-x  docs/           ← 가이드 문서
drwxr-xr-x  manifests/      ← K8s 매니페스트
drwxr-xr-x  .github/        ← GitHub Actions 워크플로우
-rw-r--r--  .gitignore
```

**5-3. Maven Wrapper 실행 권한 설정** ⚠️ Java 실습 필수

```bash
# Java 앱의 mvnw 파일에 실행 권한 부여 (macOS/Linux 필수)
chmod +x app/java-app/mvnw

# 확인
ls -la app/java-app/mvnw
```

```
예상 출력:
-rwxr-xr-x  app/java-app/mvnw
```

> 💡 **왜 필요한가?** Git은 파일 내용을 저장하지만 실행 권한(+x)은 OS마다 다를 수 있습니다.  
> `mvnw`는 실행 파일이므로 권한 설정이 필요합니다.

**5-4. 원본 저장소를 upstream으로 추가 (선택)**

```bash
# 원본 저장소를 upstream으로 추가 (업데이트 수신용)
git remote add upstream https://github.com/AnByoungHyun/git-argocd-lecture.git

# 리모트 확인
git remote -v
```

```
예상 출력:
origin      https://github.com/<your-username>/git-argocd-lecture.git (fetch)
origin      https://github.com/<your-username>/git-argocd-lecture.git (push)
upstream    https://github.com/AnByoungHyun/git-argocd-lecture.git (fetch)
upstream    https://github.com/AnByoungHyun/git-argocd-lecture.git (push)
```

✅ **확인**: `origin`이 본인 저장소 URL, Clone 후 `app/` 디렉토리 존재

---

### Step 6: GitHub Actions Secrets 등록

CI 파이프라인이 GHCR에 이미지를 푸시하려면 Secrets가 필요합니다.

**6-1. 저장소 Secrets 등록**

1. Fork한 본인 저장소 → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** 클릭 후 아래 2개 등록:

| Name | Value |
|------|-------|
| `GHCR_USERNAME` | GitHub 사용자명 (예: `AnByoungHyun`) |
| `GHCR_TOKEN` | 위에서 생성한 PAT 전체 값 |

**6-2. 등록 확인**

```bash
# 브라우저에서 확인 (직접 조회 불가 — 등록 여부만 표시)
open https://github.com/$GITHUB_USERNAME/git-argocd-lecture/settings/secrets/actions
```

```
예상 화면:
Repository secrets
  GHCR_TOKEN    Updated x seconds ago
  GHCR_USERNAME Updated x seconds ago
```

✅ **확인**: Secrets 2개(`GHCR_TOKEN`, `GHCR_USERNAME`) 등록 완료

---

## ✅ 확인 체크리스트

아래 항목을 모두 확인한 후 다음 가이드로 진행하세요.

```bash
# 한 번에 확인하는 명령어 모음
echo "=== kubectl ===" && kubectl get nodes
echo "=== docker ===" && docker info --format 'Server Running: {{.ServerVersion}}'
echo "=== git ===" && git --version
echo "=== 저장소 ===" && ls ~/workspace/git-argocd-lecture/app/
echo "=== mvnw 권한 ===" && ls -la ~/workspace/git-argocd-lecture/app/java-app/mvnw
```

- [ ] `kubectl get nodes` — 노드 1개 이상 `Ready` 상태
- [ ] `docker version` — Client / Server 모두 응답
- [ ] `git --version` — 버전 출력
- [ ] GitHub PAT 생성 완료 (`write:packages` 권한 포함)
- [ ] 저장소 Fork 및 Clone 완료, `app/` 디렉토리 존재 확인
- [ ] `app/java-app/mvnw` 실행 권한 (`-rwxr-xr-x`) 확인
- [ ] GitHub Actions Secrets 2개 (`GHCR_TOKEN`, `GHCR_USERNAME`) 등록 완료
- [ ] `docker login ghcr.io` — `Login Succeeded` 확인

---

**다음 단계**: [02. 샘플 앱 구조 이해 + 로컬 실행](02-sample-apps.md)
