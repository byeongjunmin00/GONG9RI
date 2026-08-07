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
- `ddl-auto: update`를 프로덕션에도 그대로 씀(학습·평가 목적 프로젝트라 별도 마이그레이션 도구(Flyway 등) 도입은 스코프 밖 — 스키마 변경 시 데이터 손실 리스크가 있다는 것만 인지).
- Railway 무료/저가 플랜의 실제 정책(슬립, 크레딧 한도 등)은 가입 후 대시보드에서 직접 확인(추측 금지, `docs/deploy-guide.md` 참고).

## 관련 코드 위치

- `Dockerfile`, `.dockerignore`, `docker-compose.yml` — 신규
- `src/main/resources/application.yaml` — `server.port: ${PORT:8080}` 추가
- `docs/deploy-guide.md` — Railway 배포 단계별 실행 안내(사용자용 runbook)
- 경위: `docs/dev/cd/deploy/changes/001-deploy.md`, 실행 로그: `docs/logs/cd/deploy/001-deploy.md`
