# 001-deploy — Docker화 + Railway 배포(CD) (로그)

## Attempt 1 — 2026-08-07

- 시도: `docs/dev/cd/deploy/changes/001-deploy.md` 승인된 계획대로, `Dockerfile`(멀티스테이지)·`.dockerignore`·`docker-compose.yml`(app+mysql+redis)을 신규 작성하고, `application.yaml`에 `server.port: ${PORT:8080}`을 추가했다(유일한 기존 코드 변경).
- `./gradlew build` — **BUILD SUCCESSFUL**, 기존 87케이스 전부 통과(포트 설정 추가로 인한 회귀 없음 확인).
- **로컬 실제 기동 검증**: 이 컴퓨터에 로컬 설치(Homebrew) MySQL이 3306 포트를 이미 쓰고 있어서, `docker compose`의 mysql 서비스와 포트 충돌 위험 — `brew services stop mysql`로 잠시 내리고 검증한 뒤 `brew services start mysql`로 원복했다(검증 전후로 로컬 개발 환경에 영향 없음을 `./gradlew build`로 재확인).
  - `docker compose up --build -d` — 3개 컨테이너(app/mysql/redis) 전부 정상 기동, mysql·redis 헬스체크 통과 후 app 기동 확인(로그: `Container gong9ri-mysql-1 Healthy`, `Container gong9ri-redis-1 Healthy`, `Container gong9ri-app-1 Started`).
  - `GET /api/products` → `200`, 캐싱 경로(Redis) 정상 응답 확인.
  - `POST /api/auth/signup` → `201`, 쓰기 경로(MySQL) 정상 응답 확인.
  - `docker compose down -v`로 정리(테스트 데이터가 담긴 볼륨까지 삭제) — 로컬 MySQL(Homebrew)의 기존 데이터와는 완전히 분리된 별도 볼륨이라 영향 없음.

## Attempt 1 (Evaluate) — 2026-08-07 ✅ PASS

- 계산적 평가: `./gradlew build` 87케이스 전부 통과(회귀 없음), `docker compose up --build` 성공, 쓰기·캐싱 두 경로 모두 실제 API 호출로 확인.
- 추론적 평가: 계획(`changes/001-deploy.md`)과 실제 구현 대조 — Dockerfile 멀티스테이지 구조·docker-compose 헬스체크 패턴(`ci.yml`과 동일)·`server.port` 환경변수화 전부 계획대로. 애플리케이션 코드 변경은 `server.port` 한 줄뿐이라는 계획의 "영향 최소화" 원칙도 지켜짐.
- Railway 실제 배포(계정 연동·플러그인 추가·환경변수 설정)는 브라우저 대시보드 조작이 필요해 AI가 대신 검증할 수 없는 부분 — `docs/deploy-guide.md`에 단계별 안내를 남기고, 사용자가 직접 진행한 뒤 실제 배포 URL 응답으로 별도 확인 예정.
- 판정: **PASS**(로컬 Docker화·docker-compose 통일 범위). Railway 실배포는 사용자 진행 후 확인 필요 — 상세: `docs/deploy-guide.md`.
