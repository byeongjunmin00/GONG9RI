#!/usr/bin/env sh
input=$(cat)
if echo "$input" | grep -qE 'AGENTS\.md|docs/'; then
  printf '{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"AGENTS.md 또는 docs/ 파일이 풀업으로 변경됐습니다. AGENTS.md부터 읽고 변경된 docs/ 파일을 확인해서 이 대화창의 규칙과 컨텍스트를 파악하고 보고하세요."}}'
fi
