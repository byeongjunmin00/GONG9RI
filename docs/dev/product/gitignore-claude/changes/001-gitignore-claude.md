# Gitignore Claude Code 내부 상태 파일 제외 (완료)

대상: product/gitignore-claude
담당: 민병준 / 전용운 / 코딩 에이전트

## 배경 / 요구
- Claude Code 등 AI 도구가 생성하는 로컬 대화/상태 파일(`.claude/`)이 커밋 diff에 포함되어 커밋 히스토리가 오염되는 현상을 막기 위해 `.gitignore`에 등록함.

## 평가(통과) 결과
- [x] `.gitignore`에 `.claude/` 추가 등록 완료.
- [x] 향후 팀원이 Claude Code를 사용할 때 로컬 세션 파일이 `git status` 및 `git diff`에 섞이지 않도록 방지.
