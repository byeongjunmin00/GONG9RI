# GONG9RI Railway 배포 안내 (직접 진행용)

이 문서는 브라우저 대시보드 조작이 필요해 AI가 대신 할 수 없는 부분 — 아래 순서대로 직접 진행하면 됨. Railway UI는 계속 바뀔 수 있어서, 메뉴 이름이 조금 다르면 비슷한 기능을 찾아서 진행하면 됨(추측하지 말고 실제 화면 기준으로).

## 실제 배포 결과 (2026-08-07)

- **배포 URL**: https://gong9ri-production.up.railway.app/
- Railway 프로젝트: `empathetic-recreation`(Railway가 자동으로 지어준 이름) / 환경: `production`
- Railway 계정은 민병준만 가입돼있음 — 위 URL은 누구나(팀원·튜터님 포함) 별도 계정·로그인 없이 그냥 브라우저로 접속 가능(공개 웹사이트와 동일). Railway 대시보드(재배포/로그/변수 수정 등 "관리")는 계정 있는 사람만 가능.
- `main`에 push할 때마다 Railway가 자동으로 재배포함(추가 조작 불필요).

## Railway를 선택한 이유 (AWS 등 대비)

- **학습 비용 대비 목표 적합성**: AWS로 하려면 EC2·VPC·보안그룹·RDS·ElastiCache 등을 하나하나 직접 만들고 연결해야 해서 그 자체로 학습 비용이 크다. 발제 목표는 "AWS 인프라를 마스터하는 것"이 아니라 "배포·CI/CD가 실제로 동작하는 걸 보여주는 것"이라, Railway처럼 GitHub 저장소 연결 한 번, DB/Redis 플러그인 추가 버튼 한 번으로 끝나는 플랫폼을 택해서 그 시간을 도전과제·AI 요구사항에 쓰기로 했다.
- **고정 DNS + 무중단 재배포가 기본 제공**: `*.up.railway.app` 고정 도메인이 자동으로 생기고, `main`에 push할 때마다 Dockerfile을 자동 감지해서 재빌드·재배포한다 — 별도 배포 파이프라인 코드를 안 짜도 되는 구조.
- **나중에 AWS로 옮겨도 손해가 없는 선택**: 이미 `Dockerfile`로 컨테이너화해뒀기 때문에, 나중에 AWS(ECS 등)로 옮기고 싶어지면 그 컨테이너 이미지를 그대로 옮기면 된다 — Railway를 먼저 택했다고 지금 작업이 버려지는 구조가 아니다.
- **2인 팀 규모에 맞는 인프라 복잡도**: 오토스케일링·블루그린 배포·멀티 리전 같은 엔터프라이즈급 배포 기법은 실제로 그 정도 트래픽을 받을 일이 없는 이 프로젝트 규모에 과하다고 판단 — Railway가 제공하는 수준(무중단 재배포, 헬스체크 기반 재시작)이면 충분하다.

## 0. 준비된 것

- `Dockerfile` — Railway가 push할 때마다 이 파일을 자동 감지해서 빌드함(별도 설정 없이 그대로 인식됨)
- `application.yaml`에 `server.port: ${PORT:8080}` — Railway가 주는 `PORT` 값으로 자동 바인딩됨(따로 설정 안 해도 됨)
- `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`REDIS_HOST`/`REDIS_PORT` — 전부 환경변수로 빼둔 상태(코드 수정 불필요, 아래에서 값만 넣어주면 됨)

## 1. Railway 가입 + 프로젝트 생성

1. [railway.app](https://railway.app)에서 GitHub 계정으로 가입/로그인
2. "New Project" → "Deploy from GitHub repo" 선택 → `GONG9RI` 저장소 선택(처음이면 Railway가 GitHub 저장소 접근 권한을 요청함 — 승인)
3. Railway가 `Dockerfile`을 자동 감지해서 첫 빌드를 시작함 — 이 시점엔 DB가 아직 없어서 앱이 뜨다가 죽을 수 있음(정상, 다음 단계에서 해결)

## 2. MySQL / Redis 플러그인 추가

1. 같은 프로젝트 안에서 "New" → "Database" → "Add MySQL" 추가
2. 같은 방식으로 "Add Redis" 추가
3. 각 플러그인을 클릭하면 "Variables" 탭에 접속 정보(호스트/포트/계정/비밀번호 등)가 보임 — **정확한 변수 이름은 그 화면에서 직접 확인**(예: `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD`, `MYSQLDATABASE`, `REDISHOST`, `REDISPORT` 같은 이름일 가능성이 높지만 버전에 따라 다를 수 있음)

## 3. 앱 서비스에 환경변수 설정

앱 서비스(GONG9RI 컨테이너) → "Variables" 탭에서 아래 6개를 추가(Raw Editor로 한 번에 붙여넣기 가능). Railway는 다른 서비스 변수를 `${{서비스이름.변수이름}}` 문법으로 참조할 수 있음 — 위 2번에서 확인한 실제 변수 이름을 그대로 넣기:

```
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=UTC&createDatabaseIfNotExist=true
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
REDIS_HOST=${{Redis.REDISHOST}}
REDIS_PORT=${{Redis.REDISPORT}}
REDIS_PASSWORD=${{Redis.REDISPASSWORD}}
```

(서비스 이름이 `MySQL`/`Redis`가 아니라 다르게 표시되면 그 이름으로 바꿔서 참조)

> **주의**: `REDIS_PASSWORD`를 빠뜨리면 안 됨 — Railway의 관리형 Redis는 비밀번호 인증이 필요한데, 로컬 Redis는 비밀번호 없이 열려있어서 이 필요성을 놓치기 쉬움(실제로 처음 배포 때 이걸 빠뜨려서 캐싱 경로만 500 에러가 났었음, `application.yaml`의 `spring.data.redis.password: ${REDIS_PASSWORD:}` 참고).

## 4. 공개 URL 생성

앱 서비스 → "Settings" → "Networking"(또는 비슷한 이름) → "Generate Domain" — Railway 서비스는 기본적으로 외부에 공개 안 돼있어서 이 버튼을 눌러야 실제 접속 가능한 URL이 생김.

## 5. 재배포 확인

위 설정 저장하면 Railway가 자동으로 재배포함(수동으로 "Redeploy" 눌러도 됨). 배포 로그에서 `Started Gong9riApplication` 같은 로그가 뜨는지 확인하고, 생성된 도메인으로 `GET /api/products` 호출해서 `200`이 오는지 직접 확인.

## 6. 이후 배포(CD)는 자동

이제부터 `main` 브랜치에 push할 때마다 Railway가 자동으로 다시 빌드·배포함(GitHub 저장소를 직접 보고 있어서, 별도 GitHub Actions 설정 불필요). CI(`ci.yml`)는 지금처럼 별도로 계속 돌아감 — CI가 실패해도 Railway가 자동으로 막아주진 않으니, push 전에 로컬에서 `./gradlew build` 통과를 확인하는 습관은 계속 유지.

## 7. 배포 고도화(도전과제) — 헬스체크 게이팅 + CI 게이팅 (2026-08-12)

오늘 실제로 겪은 502 전체 다운 사고(`docs/logs/ai/policy-rag/002-boot-decoupling.md`)를 계기로 배포 파이프라인의 구멍 두 개를 메움.

### 7-1. 헬스체크 게이팅 (코드로 해결됨, 별도 조작 불필요)

`railway.json`(레포 루트)에 `deploy.healthcheckPath: /actuator/health`를 넣어뒀음 — Railway가 이 파일을 자동으로 읽어서, 새 배포가 그 경로에서 200을 받을 때까지 이전 배포를 계속 살려두고 트래픽을 안 넘김(무중단 배포의 실제 메커니즘). 대시보드에서 따로 켤 것 없음, push하면 자동 적용됨.

### 7-2. CI 게이팅 — "Wait for CI" 켜기 (직접 해야 하는 부분)

지금은 `ci.yml`이 실패해도 Railway가 그대로 배포해버림(별개로 도는 형식적 게이트). Railway 대시보드에서 아래를 켜면, 우리가 이미 갖고 있는 GitHub Actions CI(`ci.yml`, `on: push`)가 성공할 때까지 배포가 대기하게 됨:

1. Railway 대시보드 → 앱 서비스 클릭 → **Settings**
2. **Source**(또는 비슷한 이름) 섹션에서 **Wait for CI** 토글 켜기
3. GitHub 권한 업데이트 요청이 뜨면 승인(기존 워크플로우 상태를 읽어와야 하기 때문)

켠 뒤에는 push할 때 Railway 배포가 곧바로 시작하지 않고 `WAITING`(또는 비슷한 상태)으로 CI 완료를 기다리다가, CI가 성공하면 배포를 시작하고 실패하면 배포를 건너뜀 — 배포 로그/활동(Activity) 탭에서 이 상태 전환이 실제로 보이는지 직접 확인.

## 8. 배포 실패 대응 체크리스트 (롤백 런북)

오늘 겪은 502 사고(원인: `PolicyDocumentIndexer`가 `ApplicationRunner`라 OpenAI API 실패가 `ApplicationContext` 초기화 자체를 실패시켜 전체 서비스가 죽음)를 실제 사례로 정리한 대응 순서.

1. **배포 실패를 어떻게 알아채나**: Railway 대시보드 → 앱 서비스 → **Deployments** 탭에서 최신 배포 상태 확인. 정상이면 배포 로그에 `Started Gong9riApplication in N seconds`가 찍히고 상태가 `Active`로 바뀜. 실패하면 (a) 예외 스택트레이스가 로그에 남고 배포가 `Failed`로 끝나거나, (b) 앱은 뜨는데 `/actuator/health`가 계속 200을 안 줘서 `healthcheckTimeout`(300초)까지 기다리다 `Failed`로 끝남 — 어느 쪽이든 대시보드에서 바로 보임.
2. **원인 파악 순서**: 배포 로그를 위에서부터 읽어서 어느 컴포넌트에서 예외가 났는지 확인(`ERROR` 로그, 스택트레이스의 최상위 클래스) → 비슷한 사고가 `docs/logs/`에 이미 있는지 확인(예: 이번 502 사고는 `docs/logs/ai/policy-rag/002-boot-decoupling.md`에 원인·해결 과정이 전부 기록돼 있음) → 최근 커밋(`git log`) 중 뭐가 이 배포에 새로 들어갔는지 대조.
3. **롤백 방법**: Railway 대시보드 → **Deployments** 탭 → 마지막으로 정상(`Active`/`Success`)이었던 배포를 찾아 그 배포의 메뉴에서 **Redeploy**(또는 비슷한 이름) 클릭 — 그 커밋 시점 이미지로 즉시 되돌아감. `main`에 새 커밋을 만들 필요 없음(급할 때는 대시보드 롤백이 먼저, 코드 수정은 그 다음에 여유 갖고).
4. **복구 확인**: 롤백(또는 원인 수정 후 재배포) 뒤 반드시 `GET /api/products`(200 확인) + `GET /actuator/health`(200 확인)로 실제 응답을 눈으로 확인하고 끝낸다 — 대시보드 상태가 `Active`인 것만 믿지 말 것(오늘 사고 이전엔 "Active면 정상"이라고 가정했던 게 문제였음).

## 참고 — 확인 안 된 것

- Railway 무료/저가 플랜의 실제 정책(월 크레딧 한도, 미사용 시 슬립 여부 등)은 가입 후 요금제 화면에서 직접 확인할 것 — 여기서 추측해서 적지 않음.
