# GONG9RI Railway 배포 안내 (직접 진행용)

이 문서는 브라우저 대시보드 조작이 필요해 AI가 대신 할 수 없는 부분 — 아래 순서대로 직접 진행하면 됨. Railway UI는 계속 바뀔 수 있어서, 메뉴 이름이 조금 다르면 비슷한 기능을 찾아서 진행하면 됨(추측하지 말고 실제 화면 기준으로).

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

앱 서비스(GONG9RI 컨테이너) → "Variables" 탭에서 아래 5개를 추가. Railway는 다른 서비스 변수를 `${{서비스이름.변수이름}}` 문법으로 참조할 수 있음 — 위 2번에서 확인한 실제 변수 이름을 그대로 넣기:

```
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=UTC&createDatabaseIfNotExist=true
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
REDIS_HOST=${{Redis.REDISHOST}}
REDIS_PORT=${{Redis.REDISPORT}}
```

(서비스 이름이 `MySQL`/`Redis`가 아니라 다르게 표시되면 그 이름으로 바꿔서 참조)

## 4. 공개 URL 생성

앱 서비스 → "Settings" → "Networking"(또는 비슷한 이름) → "Generate Domain" — Railway 서비스는 기본적으로 외부에 공개 안 돼있어서 이 버튼을 눌러야 실제 접속 가능한 URL이 생김.

## 5. 재배포 확인

위 설정 저장하면 Railway가 자동으로 재배포함(수동으로 "Redeploy" 눌러도 됨). 배포 로그에서 `Started Gong9riApplication` 같은 로그가 뜨는지 확인하고, 생성된 도메인으로 `GET /api/products` 호출해서 `200`이 오는지 직접 확인.

## 6. 이후 배포(CD)는 자동

이제부터 `main` 브랜치에 push할 때마다 Railway가 자동으로 다시 빌드·배포함(GitHub 저장소를 직접 보고 있어서, 별도 GitHub Actions 설정 불필요). CI(`ci.yml`)는 지금처럼 별도로 계속 돌아감 — CI가 실패해도 Railway가 자동으로 막아주진 않으니, push 전에 로컬에서 `./gradlew build` 통과를 확인하는 습관은 계속 유지.

## 참고 — 확인 안 된 것

- Railway 무료/저가 플랜의 실제 정책(월 크레딧 한도, 미사용 시 슬립 여부 등)은 가입 후 요금제 화면에서 직접 확인할 것 — 여기서 추측해서 적지 않음.
