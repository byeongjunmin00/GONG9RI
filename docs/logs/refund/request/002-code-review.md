# 002-code-review — 커밋 a6373d9 코드리뷰 (로그)

대상 커밋: `a6373d9` (feat(refund/request): 공구팀 참여 취소 + 결제 환불 요청/승인 추가)

## Review 1 — 2026-08-14

- 방법: 8개 각도(정확성 3 · 재사용/단순화/효율 3 · 설계 깊이 1 · 컨벤션 1)로 후보를 찾고, 각 후보를 별도 에이전트가 실제 코드를 다시 읽어 CONFIRMED/PLAUSIBLE/REFUTED로 검증.
- 1차 결과: 확정 6건 보고 (아래 표의 ①③④⑤⑥⑦).
- 사용자 피드백: "컨벤션 검사가 뭔가 놓친 것 같다"는 지적을 받고 재검토.
  - **놓쳤던 것**: 컨벤션 검사 에이전트가 `CLAUDE.md → AGENTS.md → workflow guide → commit-convention → code-convention`은 읽었지만, AGENTS.md 컨텍스트 맵에 별도 항목으로 있는 `docs/policy/`(비즈니스 규칙 SSOT)는 열어보지 않음.
  - `docs/policy/refund-trigger.md`(팀 마감 시 PAID 결제를 전부 REFUNDED로 일괄 전환)를 확인한 결과, `docs/dev/refund/request/design.md`가 SUCCESS 전환 엣지케이스는 상세히 분석해놓고 대칭되는 FAILED(마감 스윕) 엣지케이스는 전혀 다루지 않은 것을 발견 — 이게 ①번 버그의 근본 원인이었음.
  - **교훈**: 이 저장소에서 비즈니스 로직(환불/정산/성사판정 등)을 건드리는 커밋을 리뷰할 때는 `docs/policy/`를 반드시 확인한다. code-convention/commit-convention만으로는 부족하다.
- 2차 결과: 컨벤션 위반 1건(②) 추가, 총 7건으로 확정.

## 발견 사항 (심각도순)

| 심각도 | 파일 | 요약 |
|---|---|---|
| 높음 | `TeamDeadlineService.java:63` | ① 팀 마감 자동환불 스윕이 참여취소로 생긴 대기중(PENDING) RefundRequest를 걸러내지 않고 그대로 환불 처리 → 해당 RefundRequest가 영구 고아 상태로 남고, 이후 판매자 승인/거절 시 이미 환불된 결제에 대해 잘못된 상태가 기록됨 |
| 높음 | `RefundRequestService.java:100` | ③ `approve()`/`reject()`에 락(`findByIdForUpdate`/`@Version`)이 없어 동시 요청 시 PortOne 결제취소 API가 같은 결제에 대해 중복 호출될 수 있음 |
| 중간 | `docs/dev/refund/request/design.md:45` | ② `docs/policy/refund-trigger.md`를 참조하지 않았고 "관련 정책·의존" 섹션 자체가 없음 — SUCCESS 케이스는 분석했지만 대칭되는 FAILED 케이스 분석이 design.md에서 통째로 빠짐 (①의 근본 원인) |
| 중간 | `RefundRequestService.java:82` | ④ `createFromTeamLeave()`에 `createDirect()`가 가진 중복 PENDING 요청 방지 가드가 없음 — 재참가 후 재탈퇴 시 같은 결제에 RefundRequest 2건 생성 가능 |
| 낮음 | `TeamService.java:188` | ⑤ 마지막 참여자가 리더인 채로 탈퇴하면 `GroupBuyTeam.leader`가 삭제된 참여 기록을 계속 가리킴 (현재 API에서 노출 안 돼 지금은 무해, 잠재 리스크) |
| 낮음 | `RefundRequestRepositoryImpl.java:26` | ⑥ `requester`를 fetch join 하지만 응답 DTO에서 전혀 사용 안 함 (불필요한 조인) |
| 낮음 | `RefundRequestService.java:143` | ⑦ `requireOwner()`가 `PaymentService`의 동일 로직을 복붙 구현 (재사용 안 함) |

## 다음

- 위 항목들은 아직 코드 수정 전 — 리뷰 결과만 기록한 상태.
- ①③(높음)은 실제 정합성/중복 외부 API 호출과 직결되므로 우선 대응 권장.
- ②는 `design.md`에 FAILED 케이스 분석 + `docs/policy/refund-trigger.md` 참조 추가로 해소 가능 (코드 변경 없이 문서만).
