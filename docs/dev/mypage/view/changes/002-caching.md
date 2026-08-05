# 판매자 수익 현황(mypage/seller-revenue) Redis 캐싱

대상: mypage/view                <!-- 완료 시 이 기능의 changes/로 이동 -->
담당: 전용운

## 배경 / 요구

- `docs/policy/caching.md`가 `mypage/seller-revenue`를 캐싱 대상 1순위로 명시한다. 집계 쿼리(SUM, COUNT)라 비용이 크고 실시간성이 덜 중요하기 때문. 무효화 시점은 "결제 발생·환불 처리 시"로 정책에 이미 정해져 있다.
- 이 저장소엔 아직 Redis 의존성이 전혀 없다(`build.gradle` 확인 완료) — 이번이 최초 도입.
- 정책 문서 자체(대상/제외/TTL 규칙)는 이번 스코프에서 바꾸지 않는다. `product/list`·`product/detail`은 정책상 캐싱 대상이 맞지만 이번 스코프 밖(추후 별도 계획)이다.

## 설계

- **영향 계층**: `build.gradle`(Redis 의존성 추가), `application.yaml`(연결 설정), 신규 캐시 설정, 그리고 **Service 계층만** — `SellerMypageService.revenue()`, `PaymentService.create()`, `TeamDeadlineService.processDeadline()`. Controller·Repository엔 캐싱 로직을 두지 않는다(`docs/policy/caching.md` "계층 제약" 그대로).
- **캐시 키**: `sellerId` 단위로 `revenue()` 응답을 캐싱한다.
- **무효화 트리거** (정책의 "결제 발생·환불 처리 시" 그대로):
  - `PaymentService.create()` 완료 시 → 결제 대상 상품의 판매자(`product.getSeller().getId()`) 캐시 무효화
  - `TeamDeadlineService.processDeadline()`에서 환불(`Payment.refund()`) 발생 시 → 해당 팀 상품의 판매자 캐시 무효화 (한 팀은 상품 1개이므로 판매자도 1명)
- **TTL**: 정책상 무효화 누락에 대비한 안전장치로 반드시 건다. 구체 값은 Generate 단계에서 정한다.
- **CI**: Redis 가동이 필요해지므로 `.github/workflows/ci.yml`에 기존 MySQL 서비스 컨테이너와 같은 패턴으로 Redis 서비스 컨테이너 추가가 필요하다(`docs/logs/infra/ci-cd/001-env-config.md` 선례 참고).

## 태스크

- [ ] `build.gradle`에 Redis 캐싱 의존성 추가
- [ ] `application.yaml`에 Redis 연결 설정 추가 (로컬 기본값 + 환경변수 오버라이드, 기존 datasource 패턴과 일관)
- [ ] 캐시 설정(TTL 포함) 구성
- [ ] `SellerMypageService.revenue()`에 캐싱 적용 (키: sellerId)
- [ ] `PaymentService.create()`에 결제 대상 상품의 판매자 캐시 무효화 추가
- [ ] `TeamDeadlineService.processDeadline()`에 환불 발생 시 해당 판매자 캐시 무효화 추가
- [ ] `.github/workflows/ci.yml`에 Redis 서비스 컨테이너 추가
- [ ] 캐싱 동작 테스트 작성 (히트/무효화 시나리오)

## 평가(통과) 기준

- `./gradlew test --tests "*SellerMypage*"` 기존 11케이스 회귀 없이 통과
- 신규 캐싱 테스트 통과:
  1. 동일 sellerId `revenue()` 반복 호출 시 2회차부터 캐시 히트(레포지토리 재호출 안 함)
  2. 결제 생성(`payment/create`) 후 해당 판매자 캐시가 무효화되어 `revenue()` 재호출 시 최신 값 반영
  3. `team/deadline-check` 환불 처리 후 해당 판매자 캐시가 무효화되어 최신 값 반영
- `./gradlew build` 전체 통과 (CI 대비)
- 캐싱 로직이 Service 계층에만 있는지(Controller·Repository 미개입) 컨벤션 확인

## 리스크/전제

- 로컬·CI에 Redis 실행 필요(현재 미가동) — 이 저장소 최초 인프라 도입
- `team/deadline-check`는 팀별 독립 트랜잭션으로 처리되므로, 캐시 무효화도 팀별 처리 시점마다 즉시 이루어져야 한다(배치 전체 완료 후 일괄 무효화 아님)
- `payment/create`에서 `product.seller`는 지연 로딩 — 이미 로딩된 `product` 객체 위에서 접근하므로 추가 쿼리는 미미할 것으로 예상되나 확정은 Generate 단계 몫
- 통과 시 `docs/dev/mypage/view/design.md`의 "캐싱 후보(고도화 단계, MVP 아님)" 문구를 "구현됨"으로 갱신 필요
