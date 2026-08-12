# 006-realtime-messaging — 공구팀 정원 변동 실시간 브로드캐스트 (WebSocket/STOMP)

## Attempt 1 — 2026-08-12 ✅ PASS

- 목적: 발제 백엔드 도전과제 "실시간 메시징". 이미 있는 SSE(구매자 챗봇)는 1:1 스트림이라 "공구팀 정원 변동을 여러 참여자에게 동시에 알리는" 1:N 브로드캐스트엔 구조적으로 안 맞아서 WebSocket/STOMP로 별도 구현.
- 구현: `TeamService.joinWithLock`/`joinAtomic`에서 `TeamCapacityChangedEvent` 발행(기존 `TeamRefundedEvent`와 동일한 `AFTER_COMMIT` 패턴), `TeamCapacityChangedEventListener`가 `SimpMessagingTemplate`으로 `/topic/products/{productId}/teams`에 브로드캐스트. `WebSocketConfig`(`/ws-team` 핸드셰이크, 인메모리 심플 브로커). 프론트 `product.js`가 페이지 로드 시 자기 상품 토픽을 구독, 메시지 수신 시 기존 `loadTeams()`로 재조회.
- `./gradlew compileJava` 첫 시도 통과.

## Attempt 1 (실제 STOMP 통신 검증) — 2026-08-12 ✅ PASS

이 프로젝트에서 처음 도입하는 WebSocket 기능이라, "빌드는 되지만 실제로 메시지가 오가는지"를 실제 Spring STOMP 클라이언트로 검증했다(목 아님).

1. **실제 브로드캐스트 도착 확인**: `TeamCapacityBroadcastTest`(`@SpringBootTest(webEnvironment = RANDOM_PORT)`, 실제 커밋이 있어야 `AFTER_COMMIT`이 발동하므로 `@Transactional` 안 씀 — `TeamConcurrencyTest`와 동일한 관례)가 실제 `WebSocketStompClient`로 `/ws-team`에 접속해 `/topic/products/{productId}/teams`를 구독한 뒤 `TeamService.join(...)`을 직접 호출 → 5초 안에 정확한 페이로드(`teamId`, `currentCount=2`, `maxParticipants=5`) 수신 확인.
2. **Jackson 3 직렬화 호환성 실제 확인**: 이 프로젝트는 전체가 `tools.jackson`(Jackson 3)만 쓰는데, `spring-messaging` 7.0.8 jar를 직접 까본 결과 구버전 `MappingJackson2MessageConverter`(classic `com.fasterxml.jackson.databind`, deprecated)와 신버전 `JacksonJsonMessageConverter`(`tools.jackson`) 둘 다 존재함을 확인. 처음엔 `MappingJackson2MessageConverter`로 테스트해서 통과는 했지만 컴파일 시 deprecation 경고(`marked for removal`)가 나서, `JacksonJsonMessageConverter`로 교체 후 재실행 — 경고 없이 통과, 서버 측(Spring Boot 자동구성 브로커 컨버터)과도 정상 호환됨을 실측으로 확인.
3. **장애격리 실제 검증**: `TeamCapacityChangedEventListener.handleTeamCapacityChanged`에 임시로 `throw new RuntimeException("TEMP_FAULT_INJECTION_TEST")`를 넣고, 별도 임시 테스트 메서드로 `TeamService.join(...)`을 직접 호출 → 예외가 실제로 던져지고 로그에 남았음을 테스트 결과 XML(`TEMP_FAULT_INJECTION_TEST` 문자열)로 확인했는데도 `join()` 자체는 정상 응답(`currentCount=2`)했다. `@TransactionalEventListener(AFTER_COMMIT)` 콜백의 예외는 Spring이 로그만 남기고 호출자에게 전파하지 않는다는 문서상 동작을, "추측하지 않고 실제로 검증"하는 이 프로젝트의 원칙대로 실측으로 확인. 검증 후 리스너 코드와 임시 테스트 메서드 둘 다 즉시 원복, `git diff` 없음 확인(신규 파일이라 diff 대상 자체가 없어 파일을 직접 재확인).
4. `./gradlew clean build` 141/141 전부 통과, 회귀 없음.

## 참고 — UI 수준 실제 확인은 못 함

이 환경엔 브라우저 자동화 도구가 없어서, 서버 쪽(STOMP 메시지가 실제로 발행·도착하는 것)까지는 통합 테스트로 실측했지만, **실제 브라우저 두 탭에서 한쪽이 참가했을 때 다른 쪽 화면이 실시간으로 갱신되는지는 직접 확인하지 못했다** — 사용자가 로컬에서 `product.html?id=...`을 두 탭(또는 두 브라우저)으로 열어 한쪽에서 참가 후 다른 쪽이 자동 갱신되는지 수동으로 확인 필요.

## 참고 — Railway 배포 후 실제 WebSocket 동작 확인 필요

로컬 검증까지는 이번 스코프에서 마쳤지만, Railway가 실제로 WebSocket 업그레이드를 지원하는지는 배포 후 별도로 확인해야 한다(로컬 검증만으로는 확정 불가).
