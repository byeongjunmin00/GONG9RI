# 캐싱 정책

> **고도화 단계 작업** — 1차 MVP에서 구현하지 않는다.
> 단, MVP 개발 시에도 이 정책을 숙지하고 캐싱 친화적 구조로 짠다.

## 규칙

### 캐싱 대상 / 제외 대상

| 대상 | 캐싱 | 무효화 시점 |
|------|------|------------|
| 상품 목록·상세 | O | 해당 상품 수정·삭제 시 |
| 판매자 수익 현황 | O | 결제 발생·환불 처리 시 |
| 공구팀 목록 | X | — |
| 팀 참가·신설 | X | — |

### 계층 제약

캐싱 로직은 **Service 계층에서만** 적용한다 (Controller·Repository 금지).

## 근거 / 배경

- 상품 목록·상세는 조회 빈도가 높고 등록·수정 전까지 변하지 않아 캐싱 효과가 크다.
- 판매자 수익 현황은 집계 쿼리(SUM, COUNT)라 비용이 크고 실시간성이 덜 중요하다.
- 공구팀 목록은 참가 시마다 `current_count`가 바뀌어 오래된 값 노출 위험이 있다.
- 팀 참가·신설은 동시성 제어(`docs/db/group_buy_team.md`) 핵심 로직이라 캐싱 개입 시 정합성 위험이 있다.
- `group_buy_team.current_count`는 이미 DB 컬럼 레벨 캐싱으로 확정됐다 (`docs/ERD.md`).

## 적용 대상

- product/list, product/detail
- mypage/seller-revenue
- product/update, product/delete
- payment/create, team/deadline-check
