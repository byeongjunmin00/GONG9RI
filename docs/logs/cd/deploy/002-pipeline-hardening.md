# 002-pipeline-hardening — 배포 고도화 (헬스체크 게이팅 + CI 게이팅 + 무중단 실측) (로그)

## Attempt 1 — 2026-08-12 ✅ PASS (로컬 검증)

전용운이 정리해준 배포 파이프라인 구멍 6개 중 CI 게이팅/헬스체크 설정/무중단 배포 실측/롤백 런북을 이번에 하기로 하고, 컨테이너 메모리는 실측만, Flyway 도입은 이미 `docs/dev/cd/deploy/design.md`에 스코프 밖으로 명시돼 있어 재작업 없이 유지했다.

### 헬스체크 게이팅 — 로컬 실측

- `application.yaml`에 `management.endpoints.web.exposure.include: health` 추가, `SecurityConfig`에 `/actuator/health`만 permitAll, 루트에 `railway.json`(`deploy.healthcheckPath: /actuator/health`, `deploy.healthcheckTimeout: 300`) 신규.
- 로컬 `bootRun`(임시 Docker Redis 포함)으로 실제 확인:
  - `GET /actuator/health` → `200 {"groups":["liveness","readiness"],"status":"UP"}` (인증 없이 접근됨, DB/Redis `HealthIndicator`가 자동구성으로 이미 붙어있어 집계 상태에 반영됨)
  - `GET /actuator/env` → `401`(다른 actuator 엔드포인트는 여전히 막힘, 노출 범위가 의도대로 좁음)
  - `GET /actuator` → `401`
- Docker `HEALTHCHECK` 인스트럭션은 일부러 추가 안 함 — Railway 공식 문서(`docs.railway.com/guides/healthchecks`)로 확인한 결과 Railway는 그걸 안 읽고 자체 `railway.json`의 `healthcheckPath`만 본다. JRE 런타임 이미지엔 curl/wget이 없어서 괜히 이미지만 키우는 작업이라 스코프 밖으로 명시.

### 컨테이너 메모리 — 실측 결과 (문제 없음, 코드 변경 없음)

`docker run -m <제한값> eclipse-temurin:17-jre-jammy java -XX:+PrintFlagsFinal -version`로 실제 Dockerfile 런타임 이미지와 동일한 베이스로 실측:

| 컨테이너 메모리 제한 | MaxHeapSize | 비율 |
|---|---|---|
| 512MB | 128MB | 정확히 25% |
| 1GB | 256MB | 정확히 25% |
| 256MB | 약 126MB | (극단적으로 작은 값이라 JVM ergonomics 하한 로직이 개입한 것으로 추정, 실사용 범위(512MB~) 밖) |

`UseContainerSupport=true`, `MaxRAMPercentage=25.0`(둘 다 JDK 기본값) — Java 10+ JVM이 cgroup 메모리 한도를 이미 자동으로 인식해서 힙을 그 한도에 맞춰 조정하고 있음을 실측으로 확인. 문서만 믿지 않고 실제로 검증한 결과 이미 정상이라 Dockerfile은 변경하지 않음.

### CI 게이팅 — 설계만 완료, 실제 활성화는 사용자 액션 필요

Railway 공식 문서(`docs.railway.com/guides/github-autodeploys`)로 "Wait for CI" 기능이 실제로 존재함을 확인 — 서비스 설정의 토글 하나로 기존 `ci.yml`(`on: push`)이 성공할 때까지 배포를 `WAITING`시키고 실패 시 `SKIPPED`한다. 코드 변경 없음, `docs/deploy-guide.md` 7-2번 섹션에 안내 작성. 브라우저 대시보드 조작이라 사용자가 직접 켜야 함 — 다음 Attempt에서 실제 push로 같이 확인 예정.

### 롤백 런북

`docs/deploy-guide.md`에 "8. 배포 실패 대응 체크리스트" 신설 — 오늘 겪은 502 사고(원인: `PolicyDocumentIndexer`의 `ApplicationRunner` 부팅 필수 관문)를 실제 사례로 앉혀서 (1) 실패 감지 (2) 원인 파악 순서 (3) 대시보드 롤백 방법 (4) 복구 확인 순서로 정리.

### 회귀 확인

`./gradlew clean build` 141/141 전부 통과, 회귀 없음.

## Attempt 2 — 실제 배포 중 무중단 실측 + Wait for CI 확인

(진행 예정 — 이 작업 커밋을 push한 뒤 실제 Railway 재배포 동안 프로덕션 URL 폴링, 사용자가 Wait for CI 켠 뒤 실제 게이팅 동작 확인)
