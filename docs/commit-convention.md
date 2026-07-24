# 커밋 컨벤션 (commit-convention)

커밋 메시지를 작성할 때 이 규칙을 따른다.
표준 **Conventional Commits** 기반이며, 세부는 이 프로젝트에 맞게 조정한다.

> ⚠️ 커밋·푸시는 **사용자가 명시적으로 요청할 때만** 한다 (AGENTS.md 규칙).

## 형식

```
<type>(<scope>): <subject>

<body (선택)>

<footer (선택)>
```

- **type**: 변경 성격 (아래 표)
- **scope**: 영향 범위 = 보통 **개념/기능** (예: `team`, `team/join`, `payment`)
- **subject**: 한 줄 요약 (한글 가능, 명령형, 마침표 없음, 50자 내)

## type 종류

| type | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 변경 (design.md, changes/, 가이드 등) |
| `refactor` | 동작 변화 없는 구조 개선 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드·설정·의존성 등 잡무 |
| `style` | 포맷·세미콜론 등 (동작 무관) |

## 예시

```
feat(team/join): 공구팀 참가 API 추가

- POST /api/group-buy-teams/{id}/join
- 정원(max_participants) 초과 시 409 반환
- 관련 개발문서: docs/dev/team/join/changes/001-join.md
```

```
docs(team/join): 001-join 완료 처리 및 design.md 갱신
fix(team): 동시 참가 시 current_count 정합성 깨지는 문제 수정
```

## 워크플로우 연결

- **Generate/Evaluate 완료 후** 커밋할 때, 본문에 관련 `changes/00X` 문서를 링크하면 추적성이 좋아진다.
- 하나의 커밋은 **하나의 작업(change)** 단위를 지향한다.
