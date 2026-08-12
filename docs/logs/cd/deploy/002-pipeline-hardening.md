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

## Attempt 2 — 실제 배포 중 무중단 실측 + Wait for CI 확인 — 2026-08-12 ✅ PASS

사용자가 Railway 대시보드에서 "Wait for CI" 토글을 켠 뒤, 이번 배포 고도화 커밋(`1964dc6`)을 실제로 push해서 검증했다.

### 타임라인 (전부 실제 로그/폴링 기록, KST)

| 시각 | 이벤트 |
|---|---|
| 18:25:43 | push, GitHub Actions CI(`ci.yml`) 시작 |
| 18:27:29 | CI 성공 완료(`gh run view`로 확인, `conclusion: success`) |
| **18:27:32** | **Railway 빌드 시작**(Build Logs: `unpacking archive`) — CI 완료로부터 정확히 **3초 뒤** |
| 18:27:34~18:28:28 | `./gradlew bootJar --no-daemon` 실행(~54초) |
| 18:28:28~18:28:38 | 이미지 export + push(216.3MB) |
| 18:28:46 | 새 컨테이너 기동(`Starting Container`), 헬스체크 시작 — `Path: /actuator/health`, `Retry window: 5m0s`(우리가 설정한 `healthcheckTimeout: 300`과 정확히 일치) |
| 18:28:57 | 헬스체크 1차 시도 실패(`service unavailable` — 앱이 아직 완전히 안 뜬 상태, 정상적인 재시도 유발) |
| **18:29:03** | **헬스체크 성공** → 새 버전이 그제서야 트래픽을 받기 시작(대시보드 `ACTIVE`로 전환) |

### Wait for CI — 실제로 작동함(실측 확정)

Railway 빌드 시작(18:27:32)이 CI 완료(18:27:29)로부터 3초 뒤라는 걸 두 시스템의 독립적인 로그(GitHub Actions API `updatedAt`, Railway Build Logs 타임스탬프)로 교차 확인했다 — CI가 끝나기 전엔 빌드가 시작되지 않았다는 뜻으로, "Wait for CI"가 실제로 게이팅하고 있음이 확정됐다. (처음엔 배포 상세 화면의 반올림된 스테이지 소요시간으로 역산했더니 오히려 CI보다 먼저 시작한 것처럼 보여서 혼란이 있었는데, Build Logs의 초 단위 실제 타임스탬프로 확인하니 반올림 오차였음이 밝혀짐 — "정확한 로그로 재확인"의 가치를 다시 확인한 사례.)

### 무중단 배포 실측 — 다운타임 0

이 작업 커밋을 push하기 직전부터 `https://gong9ri-production.up.railway.app/api/products`를 1초 간격으로 6분간 폴링(`nohup` 백그라운드 루프, 총 360개 요청).

- **360/360 전부 200**, 비-200 응답 0건.
- 특히 새 컨테이너가 헬스체크에서 아직 떨어지고 있던 구간(18:28:46~18:29:03, 1차 시도 실패 포함 17초)에도 폴링은 계속 200만 기록함 — 이 구간엔 **이전 버전 컨테이너가 계속 트래픽을 받고 있었다**는 뜻으로, 헬스체크 게이팅이 설계한 그대로 무중단 컷오버를 만들어주고 있음을 초 단위로 실측 확인.
- 배포 완료 후 `curl https://gong9ri-production.up.railway.app/actuator/health` → `200 {"status":"UP"}` 실제 확인(새 코드가 실제로 서빙 중임을 재확인).

### 참고 — 첫 번째 폴링 시도는 실패해서 재시도함

처음에 `( for ... ) &` 형태로 서브셸을 직접 백그라운드시켰더니 15초 만에 조용히 죽어서(11줄만 기록) 다운타임 실측을 놓칠 뻔했다 — `nohup bash -c '...' &`로 바꿔서 재시도해 정상적으로 6분 완주시켰다(이 프로젝트에서 `bootRun`을 백그라운드로 띄울 때 썼던 것과 동일한 `nohup` 패턴).
