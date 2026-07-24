#!/bin/sh
# Stop 훅: 코드(src/)를 변경했는데 docs/logs/에 시도 기록이 없으면 종료를 차단한다.
# → "로그 남기기" 누락을 막는 완료 게이트. (docs/logs-guide.md)

input=$(cat)

# 무한루프 방지: 이미 이 훅 때문에 재시도 중이면 통과시킨다.
case "$input" in
	*'"stop_hook_active":true'*) exit 0 ;;
esac

# src/ 에 변경이 있는데 docs/logs/ 에 변경이 없으면 = 코드 짜고 로그 안 남긴 상태
src_changed=$(git status --porcelain -- src/ 2>/dev/null)
log_changed=$(git status --porcelain -- docs/logs/ 2>/dev/null)

if [ -n "$src_changed" ] && [ -z "$log_changed" ]; then
	echo "코드(src/)를 변경했는데 docs/logs/에 기록이 없습니다. docs/logs-guide.md에 따라 이번 Attempt(시도/결과)를 docs/logs/에 남긴 뒤 종료하세요." >&2
	exit 2   # 종료 차단 → 이유가 Claude에게 전달됨
fi

exit 0
