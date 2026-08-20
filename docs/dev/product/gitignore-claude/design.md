# Gitignore Claude Code 내부 상태 파일 제외 — Design

## 개요
Claude Code CLI 등 AI 도구 사용 시 생성되는 대화 히스토리 및 로컬 상태 폴더(`.claude/`)가 Git 커밋 diff에 섞이는 현상을 방지하기 위해 `.gitignore`에 제외 항목으로 등록한다.

## 관련 코드
- `.gitignore`: `### Claude Code Local State ### \n .claude/`

## 효과
- 팀원 및 본인이 AI 도구를 사용할 때 생기는 로컬 세션/상태 파일이 Git commit/diff에 섞이지 않고 오직 소스코드만 깔끔하게 커밋되도록 보장함.
