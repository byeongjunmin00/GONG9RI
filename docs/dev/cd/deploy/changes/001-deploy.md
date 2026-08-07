# Docker화 + Railway 배포(CD) (cd/deploy)

대상: cd/deploy
담당: 민병준

## 배경 / 요구

발제 백엔드 필수 11개 중 유일하게 남은 "배포·CI/CD"를 채움. 지금은 CI(GitHub Actions)만 있고 실제 배포가 없음. 팀 순서 논의에서 QueryDSL 다음으로 민병준이 담당하기로 함. 배포 플랫폼은 로드맵에서 이미 추천한 Railway로 확정. 이번 기회에 로컬 개발 환경도 docker-compose로 통일한다 — 지금 전용운은 Docker 컨테이너(`gong9ri-mysql`, `gong9ri-redis`), 민병준은 로컬 설치 MySQL/Redis(Homebrew)를 써서 환경이 갈라져 있었음.

## 설계

- **Dockerfile(멀티스테이지)**: `eclipse-temurin:17-jdk-jammy`로 `./gradlew bootJar` 빌드 → `eclipse-temurin:17-jre-jammy`에 jar만 복사해 실행. 의존성 레이어(`gradlew`/`gradle/`/`build.gradle`/`settings.gradle`)를 `src/` 복사보다 먼저 COPY해 레이어 캐싱.
- **docker-compose.yml**: `app`(Dockerfile 빌드)+`mysql:8`(볼륨 영속화)+`redis:7`. `app`은 두 서비스의 헬스체크(`mysqladmin ping`, `redis-cli ping` — `ci.yml`과 동일 패턴) 통과 후(`depends_on: condition: service_healthy`) 기동. DB/Redis 접속 정보는 이미 있는 `${DB_URL:...}`/`${REDIS_HOST:...}` 환경변수 오버라이드 패턴을 그대로 compose `environment:`로 주입 — 애플리케이션 코드 변경 없음.
- **`application.yaml`에 `server.port: ${PORT:8080}` 추가**: Railway 등 PaaS가 `PORT` 환경변수로 리스닝 포트를 동적 지정하는데, 지금은 포트 설정 자체가 없어 8080 고정이었음. 이번 작업의 유일한 애플리케이션 코드 변경.
- **Railway 배포는 대시보드 연동**(코드 아님): Railway가 GitHub 저장소를 직접 보고 push마다 Dockerfile을 자동 감지해 빌드·배포 — 별도 GitHub Actions 배포 워크플로우 불필요. 계정 생성·GitHub 연동·MySQL/Redis 플러그인 추가·환경변수 설정은 브라우저 대시보드 조작이라 AI가 대신 할 수 없어, 사용자가 직접 따라 할 단계별 안내(`docs/deploy-guide.md`)를 별도로 작성.

## 태스크

- [ ] `Dockerfile`(멀티스테이지)
- [ ] `.dockerignore`
- [ ] `docker-compose.yml`(app+mysql+redis)
- [ ] `application.yaml`에 `server.port: ${PORT:8080}` 추가
- [ ] 로컬에서 `docker compose up --build`로 실제 기동·API 응답 확인
- [ ] `docs/deploy-guide.md`(Railway 단계별 실행 안내) 작성
- [ ] `docs/dev/cd/deploy/design.md` 작성

## 평가(통과) 기준

- `docker compose up --build`로 앱이 정상 기동하고, 쓰기 경로(회원가입)와 캐싱 경로(상품 목록)가 모두 정상 응답
- `./gradlew build` 기존 87케이스 회귀 없음(포트 설정 추가 영향 없음 확인)
- 로컬 MySQL(Homebrew) 등 기존 개발 환경에 영향 없이 원복 확인
