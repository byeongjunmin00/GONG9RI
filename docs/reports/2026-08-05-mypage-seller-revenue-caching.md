# 개발 보고서 — 판매자 수익 현황(mypage/seller-revenue) Redis 캐싱

- **작성일**: 2026-08-05
- **작업자**: 전용운
- **대상 기능**: `mypage/view` (기존 기능 수정 — 신규 기능 아님)
- **관련 문서**: [계획(완료 이관)](../dev/mypage/view/changes/002-caching.md) · [design.md](../dev/mypage/view/design.md) · [실행 로그](../logs/mypage/view/002-caching.md) · [캐싱 정책](../policy/caching.md)

---

## 1. 배경 / 목적

`docs/policy/caching.md`가 캐싱 대상으로 지정한 두 항목(`product/list`·`product/detail`, `mypage/seller-revenue`) 중 **`mypage/seller-revenue`**(판매자 수익 현황)를 우선 구현했다. 이 엔드포인트는 결제 테이블에 대한 `SUM`/`COUNT` 집계 쿼리라 조회 비용이 크고, 실시간성 요구가 낮아 캐싱 효과가 크다고 판단된 항목이다.

이 저장소엔 Redis 의존성이 전혀 없던 상태라, 이번 작업이 **최초의 Redis 도입**이었다.

## 2. 범위

| 포함 | 제외 |
|---|---|
| `GET /api/seller/mypage/revenue` 응답 캐싱 | `product/list`, `product/detail` 캐싱 (별도 계획 대상) |
| `payment/create` 시점 캐시 무효화 | 캐싱 정책 문서 자체 변경 |
| `team/deadline-check`(환불) 시점 캐시 무효화 | |

## 3. 설계 요약

- **캐시 대상**: `SellerMypageService.revenue()`의 응답(`RevenueResponse`), **판매자(sellerId) 단위**로 캐싱.
- **무효화 트리거** (정책 그대로 — "결제 발생·환불 처리 시"):
  - `PaymentService.create()` 완료 직후, 결제 대상 상품의 판매자 캐시 무효화 (teamId 유무 무관, 항상 실행).
  - `TeamDeadlineService.processDeadline()`에서 실제로 환불이 발생한 경우(`paidPayments` 비어있지 않을 때)만 해당 팀 상품의 판매자 캐시 무효화. 이 메서드가 팀별 독립 트랜잭션이라, 무효화도 배치 전체 완료 후가 아니라 그 트랜잭션 안에서 즉시 수행.
- **TTL**: 10분 (`docs/policy/caching.md`의 "무효화 누락 대비 안전장치" 요구 반영).
- **계층 제약**: 캐싱 로직은 Service 계층(`SellerMypageService`/`PaymentService`/`TeamDeadlineService`)에만 존재. Controller·Repository는 미개입.
- **무효화 구현 방식**: `@CacheEvict` 대신 `CacheManager`를 직접 주입해 명시적으로 `evict()` 호출. 이유: `product`/`team.getProduct()`의 seller id는 메서드 파라미터가 아니라 본문에서 조회한 값이라, `@CacheEvict`의 SpEL(메서드 파라미터만 참조 가능)로는 표현할 수 없었음.

## 4. 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `build.gradle` | `spring-boot-starter-cache`, `spring-boot-starter-data-redis` 추가 |
| `src/main/resources/application.yaml` | `spring.data.redis.host/port`(환경변수 오버라이드), `spring.cache.type: redis` 추가 |
| `src/main/java/.../config/CacheConfig.java` **(신규)** | `@EnableCaching`, `sellerRevenue` 캐시명, TTL 10분 + JSON 값 직렬화기 커스터마이즈 |
| `src/main/java/.../service/SellerMypageService.java` | `revenue()`에 `@Cacheable(key = "#principal.member.id")` 적용 |
| `src/main/java/.../service/PaymentService.java` | 결제 생성 후 판매자 캐시 무효화 (`CacheManager` 직접 호출) |
| `src/main/java/.../service/TeamDeadlineService.java` | 환불 발생 시 판매자 캐시 무효화 (동일 방식) |
| `.github/workflows/ci.yml` | 기존 `mysql` 서비스 컨테이너 패턴으로 `redis:7` 서비스 컨테이너 추가 |
| `src/test/resources/application.yaml` **(신규)** | 테스트 프로파일 `spring.cache.type: simple` — 실제 Redis 없이 `@Cacheable`/무효화 동작 검증 |
| `src/test/java/.../config/CacheConfigTest.java` **(신규)** | 직렬화기 설정 자체를 검증하는 순수 단위 테스트 |
| `src/test/java/.../service/SellerRevenueCachingTest.java` **(신규)** | 캐시 히트·무효화 시나리오 4케이스 (5절 참고) |
| `docs/dev/mypage/view/design.md` | 캐싱 구현 내용으로 최종 갱신 |
| `docs/dev/mypage/view/changes/002-caching.md` | 계획 문서 채번 이관 (완료 기록) |

## 5. 구현 중 발견·수정된 결함 (Generate 루프)

같은 접근으로 해결 가능한 범위라 재승인 없이 2회 재시도했다(`AGENTS.md` 루프 규칙).

1. **직렬화 결함(Attempt 1 → 2)**: `RevenueResponse`가 `Serializable`을 구현하지 않는 `record`인데, 운영 설정(`spring.cache.type: redis`)에서 Spring Boot 기본 직렬화기(`JdkSerializationRedisSerializer`)는 `Serializable` payload를 요구한다. 실제 Redis에 캐싱하는 시점에 예외가 날 결함이었음 → `CacheConfig`에서 JSON 직렬화기를 명시적으로 설정해 해결.
2. **타입 소실 결함(Attempt 2 내부에서 추가 발견)**: 처음엔 범용 JSON 직렬화기(`GenericJacksonJsonRedisSerializer`)를 썼는데, Spring Cache 추상화가 조회 시 목표 타입을 넘기지 않아 캐시 히트 시 `RevenueResponse` 대신 `LinkedHashMap`으로 역직렬화되는 문제를 자체 테스트로 발견 → `RevenueResponse` 타입을 고정한 직렬화기(`JacksonJsonRedisSerializer<>(RevenueResponse.class)`)로 교체해 해결.

두 결함 모두 테스트 프로파일이 `spring.cache.type: simple`(인메모리)로 우회하고 있어 일반 테스트로는 드러나지 않았고, 코드 리뷰(추론적 평가)·직렬화 왕복 단위 테스트로 확정했다.

## 6. 테스트

`SellerRevenueCachingTest.java` — 대량 더미 데이터(PAID 25건 + REFUNDED 5건, 손으로도 합계 검증 가능한 금액 구성)를 기반으로 4가지 시나리오 검증:

1. **캐시 히트 증명**: 첫 조회 후, 무효화 경로를 거치지 않고 레포지토리에 결제를 직접 추가해 실제 DB 데이터를 바꾼 뒤 재조회 → 값이 그대로임을 확인해 "우연히 안 바뀐 것"이 아니라 "진짜 캐시된 값"임을 증명.
2. **결제 생성 무효화**: 대량 베이스라인 위에 새 결제 발생 → `before`/`after` 값이 정확한 금액·건수 delta로 달라짐을 확인(`assertNotEquals` + 정확한 기대값).
3. **환불 처리 무효화**: 특정 팀의 결제만 정확히 차감되고, 무관한 기존 결제(베이스라인)는 그대로 남아있는지 확인.
4. **무효화 안 됨 확인**: 환불이 없는 마감 처리는 캐시를 건드리지 않음(레포지토리 재호출 없음).

`CacheConfigTest.java` — 실제 Redis 연결 없이 직렬화기 설정 자체(write→read 왕복)를 검증하는 순수 단위 테스트.

## 7. 검증 결과

작업 도중 로컬에 Docker Desktop이 없어(→ 사용자가 설치) → WSL2 미설치(→ 사용자가 설치/활성화) → Docker Desktop의 Docker AI(Inference) 기능이 사용자 홈 경로의 한글 문자 때문에 크래시 루프를 도는 이슈(→ 해당 기능만 비활성화로 우회)를 순서대로 해결한 뒤, 실제 `mysql:8`/`redis:7` 컨테이너로 최초 엔드투엔드 검증을 수행했다.

| 항목 | 결과 |
|---|---|
| `SellerMypageControllerTest` (기존, 11케이스) | ✅ 11/11 통과 |
| `SellerRevenueCachingTest` (신규, 4케이스) | ✅ 4/4 통과 |
| `CacheConfigTest` (신규, 1케이스) | ✅ 1/1 통과 |
| `./gradlew build` (CI와 동일, 저장소 전체) | ✅ BUILD SUCCESSFUL, 전체 75개 테스트 전부 통과 |

## 8. 컨벤션·정책 준수 확인

- ✅ 캐싱 로직 Service 계층 한정 (`docs/policy/caching.md` 계층 제약)
- ✅ TTL 안전장치 부여 (10분)
- ✅ 무효화 트리거 시점(결제 발생·환불 처리) 정책과 일치
- ✅ 생성자 주입, `final` 필드, `@Transactional(readOnly=true)` 기본 패턴 (`docs/code-convention.md`)
- ✅ 신규 결정 사항(직렬화 방식)은 코드 주석으로 "왜"만 남김

## 9. 남은 사항 / 참고

- 이번 스코프는 `mypage/seller-revenue` 하나로 한정. `product/list`·`product/detail` 캐싱은 정책상 대상이 맞지만 별도 계획 필요.
- 로컬 검증용으로 띄운 `gong9ri-mysql`/`gong9ri-redis` Docker 컨테이너는 CI 설정과 무관한 개인 환경이므로 필요 없어지면 정리 대상.
- 커밋/푸시는 아직 수행하지 않음 — 사용자 확인 후 진행 예정.
