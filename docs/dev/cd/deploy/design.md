# Docker화 + Railway 배포(CD) (cd/deploy) — Design

## 개요

발제 백엔드 필수 항목 "배포·CI/CD"를 채우는 인프라 작업. `Dockerfile`로 이 앱을 컨테이너화하고, `docker-compose.yml`로 로컬 개발 환경(app+MySQL+Redis)을 통일하며, Railway가 GitHub 저장소를 직접 보고 push마다 자동 빌드·배포하는 방식으로 CD를 구성한다.

## API / 인터페이스

- 대상 기능이 아니라 배포/인프라 계층이라 REST 엔드포인트 변화 없음.

## 데이터 모델

- 신규 테이블·컬럼 없음. Railway MySQL/Redis 플러그인이 기존과 동일한 스키마를 그대로 씀(`ddl-auto: update`가 최초 배포 시 테이블을 만듦).

## 구성 요소

- **`Dockerfile`(멀티스테이지)**: 1단계(`eclipse-temurin:17-jdk-jammy`)에서 `./gradlew bootJar`로 빌드(QueryDSL 애노테이션 프로세싱은 `bootJar` 안에서 이미 처리됨, 별도 대응 불필요), 2단계(`eclipse-temurin:17-jre-jammy`)에서 빌드된 jar만 복사해 실행. `EXPOSE 8080`, `ENTRYPOINT ["java", "-jar", "app.jar"]`.
- **`docker-compose.yml`**: `app`(Dockerfile 빌드, 8080 포트) + `mysql:8`(볼륨 `gong9ri_mysql_data`로 영속화) + `redis:7`. `app`은 두 서비스가 `service_healthy`(헬스체크: `mysqladmin ping` / `redis-cli ping`, `.github/workflows/ci.yml`과 동일 패턴)가 될 때까지 기다린 뒤 기동. 접속 정보는 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`REDIS_HOST`/`REDIS_PORT` 환경변수로 주입 — 이미 `application.yaml`에 있던 `${...}` 오버라이드 패턴을 그대로 재사용(신규 코드 없음).
- **`application.yaml`의 `server.port: ${PORT:8080}`**: Railway가 `PORT` 환경변수로 리스닝 포트를 동적 지정하므로, 로컬(기본값 8080)과 배포 환경(Railway가 주입하는 값) 모두에서 정확히 그 포트로 바인딩되게 함.
- **Railway 배포(대시보드 연동, 코드 아님)**: Railway 프로젝트에 GitHub 저장소를 연결하면, `Dockerfile`을 자동 감지해 push마다 빌드·배포한다. MySQL/Redis는 Railway의 관리형 플러그인을 추가해서 쓰고, 앱 서비스의 환경변수(`DB_URL` 등)에 그 플러그인들의 접속 정보를 연결한다(Railway의 서비스간 변수 참조 문법 `${{MySQL.MYSQL_URL}}` 등 활용). 계정 생성·연동·환경변수 설정은 브라우저 대시보드 조작이라 사용자가 직접 함 — 단계별 안내는 `docs/deploy-guide.md`.

## 규칙 / 검증

- 로컬 개발은 이제 두 가지 방법 다 가능: (1) `docker compose up --build`(팀 전체 동일 환경, 추천) (2) 기존처럼 로컬 설치 MySQL/Redis + `./gradlew bootRun`(그대로 유지, 강제 전환 아님).
- `ddl-auto: update`를 프로덕션에도 그대로 씀(학습·평가 목적 프로젝트라 별도 마이그레이션 도구(Flyway 등) 도입은 스코프 밖 — 스키마 변경 시 데이터 손실 리스크가 있다는 것만 인지). **2026-08-12 재확인**: 배포 고도화(도전과제) 작업 중 이 결정을 다시 검토했는데, DB 마이그레이션 전략 도입은 "배포 파이프라인"과는 결이 다른 별개의 다단계 작업(기존 스키마 베이스라인화, 마이그레이션 파일화, `ddl-auto` 전환)이라 여전히 스코프 밖으로 유지하기로 함 — 재작업 없음.
- Railway 무료/저가 플랜의 실제 정책(슬립, 크레딧 한도 등)은 가입 후 대시보드에서 직접 확인(추측 금지, `docs/deploy-guide.md` 참고).

## 배포 고도화 (발제 백엔드 도전과제, 2026-08-12)

전용운의 `8b39136`(`PolicyDocumentIndexer`가 `ApplicationRunner`라 OpenAI 임베딩 API 실패가 `ApplicationContext` 초기화 자체를 실패시켜 로그인·상품·결제 등 무관한 서비스까지 전부 502로 죽었던 사고 후속조치)이 이 작업의 실제 근거다. 그 커밋은 이번 사고의 증상(색인 로직)만 없앴고, "부팅 중 예외 하나가 배포 전체를 끌고 내려가는" 구조적 위험 자체는 남아있었다.

- **CI 게이팅**: Railway의 "Wait for CI" 서비스 설정(대시보드 토글, 코드 아님) — 기존 `ci.yml`(`on: push`)이 성공할 때까지 배포를 `WAITING`으로 미루고, 실패하면 `SKIPPED`한다. 지금까진 `ci.yml`은 별개로 도는 형식적 게이트였고(CI가 깨져도 Railway는 그대로 배포), 이걸로 실제 게이트가 됨.
- **헬스체크 게이팅**: `railway.json`(루트, config-as-code) — `deploy.healthcheckPath: /actuator/health`, `deploy.healthcheckTimeout: 300`. 새 배포가 이 경로에서 200을 받을 때까지 Railway가 이전 배포를 계속 살려두고 트래픽을 안 넘긴다 — 이게 무중단 배포의 실제 메커니즘(배포 시작 시점에만 쓰이고, 활성화 이후엔 상시 모니터링 안 함 — K8s liveness/readiness 같은 상시형이 아니라 이 규모엔 이 정도로 충분). `application.yaml`에 `management.endpoints.web.exposure.include: health`로 그 경로만 명시적으로 노출(다른 actuator 엔드포인트는 계속 막힘), `SecurityConfig`에 `/actuator/health`만 permitAll(인증 요구 시 Railway 프로버가 401 받고 배포가 영원히 대기하게 됨). DB/Redis `HealthIndicator`는 각 스타터가 클래스패스에 있으면 자동구성으로 이미 붙어있어서, DB나 Redis가 끊긴 상태로 새 배포가 뜨면 헬스체크가 실제로 DOWN을 내서 컷오버를 막아준다(별도 코드 없음). Docker `HEALTHCHECK` 인스트럭션은 일부러 안 넣음 — Railway는 그걸 안 읽고 자체 `healthcheckPath`만 본다(공식 문서로 확인), JRE 런타임 이미지엔 curl/wget도 없어서 괜히 이미지만 커짐.
- **무중단 배포 실측**: 기존엔 "Railway가 제공하는 수준(헬스체크 기반 재시작)이면 충분하다"고 문서에만 적혀있고 실측 근거가 없었음. 이번 작업 커밋을 실제로 push해서 재배포되는 동안 프로덕션 URL을 폴링해서 실측 — 결과는 `docs/logs/cd/deploy/002-pipeline-hardening.md`.
- **컨테이너 메모리**: `docker run -m <제한값> eclipse-temurin:17-jre-jammy java -XX:+PrintFlagsFinal -version`로 실측 — 512MB 제한→힙 128MB, 1GB 제한→힙 256MB, 둘 다 정확히 `MaxRAMPercentage=25%`(JDK 기본값) 그대로 컨테이너 한도에 맞춰 자동 조정됨(`UseContainerSupport=true`가 기본으로 켜져있음, Java 10+). 실제 문제가 없어서 Dockerfile은 변경 안 함 — 추측 대신 실측으로 "이미 괜찮다"를 확인한 사례.
- **롤백 런북**: `docs/deploy-guide.md`에 "배포 실패 대응 체크리스트" 신설, 오늘 겪은 502 사고를 실제 사례로 앉힘.

## 관련 코드 위치

- `Dockerfile`, `.dockerignore`, `docker-compose.yml` — 신규
- `src/main/resources/application.yaml` — `server.port: ${PORT:8080}` 추가, `management.endpoints.web.exposure.include: health` 추가
- `src/main/java/com/gong9ri/gong9ri/config/SecurityConfig.java` — `/actuator/health` permitAll 추가
- `railway.json` — 신규, 헬스체크 경로 설정
- `docs/deploy-guide.md` — Railway 배포 단계별 실행 안내(사용자용 runbook), Wait for CI 안내, 롤백 런북
- 경위: `docs/dev/cd/deploy/changes/{001-deploy,002-pipeline-hardening}.md`, 실행 로그: `docs/logs/cd/deploy/{001-deploy,002-pipeline-hardening}.md`
