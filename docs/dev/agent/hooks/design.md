# 라이프사이클 훅 (agent/hooks) — Design

## 개요

에이전트 라이프사이클 훅(`.agents/hooks.json` 및 `.claude/hooks/`)을 통해 `git push` 후 TIL 노트 작성 강제(`require-til.sh`), `src/` 수정 후 시도 기록(log) 작성 강제(`require-log.sh`), push 시점 스냅샷 마킹(`post-push-mark.sh`)을 자동화한다.

## 훅 프로토콜 규격 (Antigravity SSOT)

- **`Stop` 훅 (`require-til.sh`, `require-log.sh`)**:
  - 차단 조건 발생 시 Antigravity JSON 프로토콜을 `stdout`으로 반환:
    `{"decision": "continue", "reason": "<사유 안내 문구>"}`
  - 조건 해소 시 `{}` 반환.
- **`PostToolUse` 훅 (`post-push-mark.sh`)**:
  - `run_command` 실행 내용 중 `git push` 포함 시 `.claude/.last-push-date` 마커를 찍고 `{}` 반환.

## 관련 코드 위치

- `.agents/hooks.json` — Antigravity 훅 이벤트 바인딩
- `.claude/hooks/require-til.sh` — TIL 작성 강제 스크립트
- `.claude/hooks/require-log.sh` — 시도 기록 작성 강제 스크립트
- `.claude/hooks/post-push-mark.sh` — push 시점 마킹 스크립트
