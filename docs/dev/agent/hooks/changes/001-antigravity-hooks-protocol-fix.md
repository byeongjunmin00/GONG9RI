# Antigravity(AGY) 훅 프로토콜 호환성 수정 (require-til, require-log)

대상: agent/hooks
담당: 전용운

## 배경 / 요구

- 현재 `.claude/hooks/`에 작성된 훅 스크립트(`require-til.sh`, `require-log.sh`, `post-push-mark.sh`)가 Claude Code 방식(stderr 출력 + exit 2)으로 되어 있어, **Antigravity(AGY) 환경에서 세션 종료(Stop) 시 훅이 `decision: continue` 판정을 내리지 못하고 우회/종료되는 문제**가 발생함.
- **목적**:
  - `Stop` 훅 (`require-til.sh`, `require-log.sh`)이 차단 필요 시 Antigravity JSON 프로토콜(`{"decision": "continue", "reason": "..."}`)을 stdout으로 출력하도록 수정.
  - `PostToolUse` 훅 (`post-push-mark.sh`, `pull-docs-check.sh`)이 정상 JSON 규격(`{}`)을 반환하도록 호환성 보완.
  - `git push` 성공 후 TIL이 없는 경우 세션 종료가 자동으로 정상 차단되도록 강제함.

---

## 설계 및 변경 방향

### 1. `require-til.sh` (`Stop` 훅)
- `MARKER`(`.claude/.last-push-date`) 존재 확인 후, Obsidian Vault에 마커 시각 이후 작성된 TIL 노트가 없으면:
  - Antigravity 규격에 맞춰 stdout으로 JSON 출력:
    `{"decision": "continue", "reason": "git push는 성공했는데 그 이후로 Obsidian Vault에 TIL을 새로 쓰거나 고친 기록이 없습니다. TIL을 작성한 뒤 종료하세요."}`
  - exit 0으로 반환 (Antigravity는 stdout JSON의 decision으로 판단함).
- TIL이 이미 존재하면 `{}` 출력 후 마커 제거.

### 2. `require-log.sh` (`Stop` 훅)
- `src/` 변경 시 `docs/logs/`에 기록이 없으면:
  - stdout으로 JSON 출력:
    `{"decision": "continue", "reason": "코드(src/)를 변경했는데 docs/logs/에 기록이 없습니다. docs/logs-guide.md에 따라 이번 Attempt를 남긴 뒤 종료하세요."}`
- 조건 충족 시 `{}` 출력.

### 3. `post-push-mark.sh` (`PostToolUse` 훅)
- `run_command` 실행 내용 중 `git push` 확인 시 마커 파일(`.claude/.last-push-date`) 생성 후 `{}` JSON 출력.

---

## 태스크

- [ ] `.claude/hooks/require-til.sh` 수정 (Antigravity JSON stdout 계약 반영)
- [ ] `.claude/hooks/require-log.sh` 수정 (Antigravity JSON stdout 계약 반영)
- [ ] `.claude/hooks/post-push-mark.sh` 수정 (Antigravity JSON stdout 계약 반영)
- [ ] 훅 스크립트 실행 및 동작 검증 (push 마커 동작 및 Stop 훅 JSON 반환 확인)
- [ ] 관련 개발 문서 정리 (`ongoing` → `changes/`)

---

## 평가(통과) 기준

- `git push` 완료 후 TIL 노트를 작성하지 않은 상태에서 세션 종료 시, Antigravity 훅 엔진이 이를 차단하고 `{"decision": "continue", "reason": "..."}` 안내 문구를 주입하여 세션 지속.
- TIL 노트 작성 후에는 마커가 정상 해소되고 세션 종료 허용.
- `src/` 수정 후 log 누락 시 세션 종료 차단 및 안내 문구 주입.

---

## 리스크 / 전제

- Git Bash (`sh.exe`) 경로가 Windows 환경에서 제대로 훅을 실행할 수 있도록 `.agents/hooks.json`의 커맨드 경로 유지.
