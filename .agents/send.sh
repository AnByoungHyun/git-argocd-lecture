#!/bin/bash
# =====================================================
# 에이전트 메시지 전송 헬퍼
# 사용법: send.sh <from> <to> "message"
#         send.sh backend frontend "API 변경됨"
#         send.sh planner all "작업 시작"
# =====================================================

FROM="$1"
TO="$2"
shift 2
MSG="$*"

# .agents/pipes 디렉토리를 프로젝트 루트 기준으로 탐색
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PIPE_DIR="$SCRIPT_DIR/pipes"

PIPE="$PIPE_DIR/from-${FROM}"

if [ ! -p "$PIPE" ]; then
  echo "ERROR: Pipe $PIPE not found. Is the router running?"
  exit 1
fi

printf "TO:%s\n%s\n" "$TO" "$MSG" > "$PIPE" &
echo "[sent] ${FROM} => ${TO}: ${MSG}"
