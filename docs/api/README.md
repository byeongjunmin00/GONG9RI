# api — API 명세(계약)

REST API **계약의 진실의 원천(SSOT)**이다. 요청/응답 형식을 한 곳에 모아 클라이언트·서버(팀원 둘)가 공유한다.

## 위치 & 이름

```
docs/api/
├── README.md
└── {개념}.md            예: team.md  (그 개념의 엔드포인트 모음)
```

## API 문서 템플릿

```markdown
# team API

## POST /api/group-buy-teams/{productId}/teams  — 공구팀 신설
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | (없음, 로그인 사용자 = leader) | | | |
- 응답: `201 Created` → `{ "teamId": 1, "status": "RECRUITING", "currentCount": 1, ... }`
- 에러: `404`(상품 없음), ...

## POST /api/group-buy-teams/{teamId}/join  — 공구팀 참가
- 응답: `200 OK` → `{ "teamId": 1, "currentCount": 3, "status": "RECRUITING" }`
- 에러: `409`(정원 초과, `TEAM_FULL`), `404`(팀 없음), ...
...
```

## 작성 컨벤션

- **URL**: 리소스는 **복수 명사·소문자·하이픈**. `/api/products`, `/api/group-buy-teams/{id}`
- **메서드**: `GET`(조회) · `POST`(생성) · `PUT`(전체수정) · `PATCH`(부분수정) · `DELETE`(삭제)
- **상태코드**: `200`(조회) · `201`(생성) · `204`(삭제 후 본문없음) · `400`(검증실패) · `404`(없음) · `409`(충돌, 예: 정원 초과) · `500`
- **에러 응답**: 일관된 형식 `{ "code": "...", "message": "..." }`
- **날짜/시간**: ISO-8601 (`2026-07-24T10:00:00`)
- **본문**: 요청/응답은 DTO 기준 (엔티티 직접 노출 금지 — `docs/code-convention.md`)
- **목록**: 페이지네이션 고려 (`page`, `size`)

## 관계 & 규칙

- 이 명세는 **Plan 단계에서 작성**한다. Generate는 이 명세대로 코드를 구현한다.
- `design.md`의 "API/인터페이스"는 여기(`docs/api/{개념}.md`)를 **참조**한다.
- 엔드포인트가 바뀌면 이 문서를 먼저 갱신한다 (구현 중 변경이 필요하면 Plan으로 재승인).

> 참고: 실제 API 계약은 나중에 **springdoc-openapi**로 코드에서 자동 생성할 수 있다.
> 그러면 드리프트가 최소화되므로, 규모가 커지면 이 문서를 **생성형 openapi로 승격**하는 것을 고려한다.
> (지금은 설계·계획용 손작성 문서)
