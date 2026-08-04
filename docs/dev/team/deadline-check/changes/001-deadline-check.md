# 공구팀 마감 체크 & 환불 트리거

대상: team/deadline-check
담당: 전용운

## 배경 / 요구

`team/crud`에서 미룬 작업("payment 기능이 있어야 의미가 있어 스코프 밖"으로 명시, `docs/dev/team/crud/design.md`) — 이제 payment까지 구현 완료됐으니 진행한다.
`docs/policy/refund-trigger.md`(SSOT)에 규칙이 이미 확정돼 있음: 스케줄러가 주기적으로(기본 1분) `status=RECRUITING && deadline<now()`인 `group_buy_team`을 스캔해, 대상 팀마다 하나의 트랜잭션 안에서 ①`FAILED` 전환 ②그 팀에 연결된 `PAID` 결제 전부 `REFUNDED` 일괄 전환한다.

## 설계

- **접근**: 정책(`docs/policy/refund-trigger.md`)이 정한 방식을 그대로 따른다 — `@Scheduled` 기반 주기 스캔, 팀 단위로 트랜잭션을 분리해 처리(전체 대상 팀을 한 트랜잭션으로 묶지 않음 — 정책의 "해당하는 팀은 하나의 트랜잭션 안에서"가 팀 단위를 의미).
- **영향 계층**:
  - 신규 스케줄러 컴포넌트 1개 (패키지 위치·클래스명은 Generate에서 결정)
  - `Gong9riApplication`(또는 별도 config): 스케줄링 활성화 필요 (현재 `@EnableScheduling` 없음)
  - `service`: 팀 상태 전환 + 결제 일괄 환불을 묶는 로직 (기존 `TeamService`/`PaymentService`에 추가할지, 새 서비스로 분리할지는 Generate 판단)
  - `repository`: "RECRUITING + deadline 지난 팀" 조회 쿼리 신규(기존 `idx_status_deadline` 인덱스 활용 가능), "팀 ID로 PAID 결제 조회/일괄 전환" 신규(기존 `idx_team_status` 인덱스 활용 가능)
  - `entity`: `GroupBuyTeam`에 `FAILED` 전환용 도메인 메서드 필요할 수 있음(기존 `increaseParticipant()`와 같은 패턴). `Payment.refund()`는 이미 존재(mypage 작업 때 추가, 아직 실제 호출 지점 없음).
  - API/컨트롤러/DTO 변경 없음 (내부 배치, 사용자 노출 엔드포인트 아님)
  - 신규 테이블 없음, 신규 `ErrorCode` 불필요(사용자 대면 에러가 아님)

## 리스크 / 전제

- 로컬 MySQL 가동 필요(기존 규칙과 동일)
- **동시성 경합 가능성**: 팀 참가(`join`, 비관적 락 사용)와 마감 체크 스케줄러가 같은 팀 row를 거의 동시에 건드릴 수 있음(예: 마감 직전 마지막 참가 시도와 스케줄러의 FAILED 처리가 겹치는 경우) — 해결 방식(락 전략)은 Generate에서 결정.
- 스케줄러 주기(정책 기본값 1분)는 실제 부하·정확도 요구에 따라 Generate에서 조정 가능(정책에 이미 명시됨).
- 테스트 시 실제 1분 대기 없이 "마감 지난 팀"을 검증하는 방법이 필요함 — 테스트 설계는 Generate 몫.
- 단일 인스턴스 로컬 운영 기준. 다중 인스턴스 배포 시 중복 실행 문제는 이번 스코프 밖.

## 평가(통과) 기준

- `RECRUITING` + `deadline` 지난 팀 → `FAILED` 전환 확인
- 해당 팀에 연결된 `PAID` 결제 전부 `REFUNDED` 전환 확인
- `deadline`이 아직 안 지난 `RECRUITING` 팀은 영향 없음(그대로 유지)
- 이미 `SUCCESS`/`FAILED`인 팀은 스캔 대상에서 제외됨
- 연결된 결제가 없는 팀도 에러 없이 정상 처리됨
- `./gradlew test --tests "*DeadlineCheck*"` (또는 대상 기능명 기준) 통과
