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

## 9. 로그인 고도화 2단계 — 이메일 인증/비밀번호 재설정 배포 준비 (2026-08-12)

`docs/dev/auth/email-verification/design.md`, `docs/dev/auth/password-reset/design.md` 기능을 실제로 쓰려면 아래 두 가지를 **직접** 해야 함 — 코드만 배포해서는 메일이 안 나가고, DB 마이그레이션도 자동으로 안 걸림.

### 9-1. 환경변수 2개 추가 (SendGrid, 2026-08-12부터)

**처음엔 Gmail SMTP(`MAIL_USERNAME`/`MAIL_PASSWORD`)로 시도했으나, Railway가 아웃바운드 SMTP(587번 포트)를 막고 있어 프로덕션에서 발송이 전혀 안 되는 걸 실측으로 확인**하고 HTTP 기반 SendGrid로 교체했다(`docs/logs/cd/deploy/004-smtp-blocked.md`). 아래 절차로 진행:

1. [sendgrid.com](https://sendgrid.com)에서 무료 가입(카드 불필요)
2. **Settings → Sender Authentication → Single Sender Verification**에서 발신용으로 쓸 이메일 주소(예: 프로젝트 관리자 Gmail) 등록 → 그 주소로 온 인증 메일의 링크 클릭(도메인 전체 인증 아님, 이메일 주소 하나만 인증 — 커스텀 도메인이 없어도 됨)
3. **Settings → API Keys**에서 API 키 발급(Full Access 또는 최소 Mail Send 권한)
4. 앱 서비스 → **Variables** 탭에서 아래 3개 추가:

```
SENDGRID_API_KEY=<발급받은 API 키>
SENDGRID_FROM_EMAIL=<2번에서 인증한 발신 주소>
APP_BASE_URL=https://gong9ri-production.up.railway.app
```

> `APP_BASE_URL`은 이메일 안의 인증/재설정 링크를 만드는 데 쓰임(`app.base-url` 설정, 없으면 로컬 기본값 `http://localhost:8080`으로 링크가 만들어져서 실제로는 안 열림). `SENDGRID_API_KEY`/`SENDGRID_FROM_EMAIL`을 안 넣어도 앱 자체는 뜨지만, 실제 메일 발송은 전부 실패해서 로그에 `이메일 발송 실패` 경고만 쌓이고 사용자는 인증 메일을 못 받는다.

### 9-2. `member.email` UNIQUE 인덱스 수동 적용

`ddl-auto: update`가 기존 컬럼에 새 UNIQUE 제약을 자동으로 안 걸어준다는 걸 로컬에서 실측 확인했음(`docs/db/member.md`의 "마이그레이션 메모" 참고) — 프로덕션 DB에도 똑같이 수동으로 걸어야 함. Railway MySQL 플러그인 → **Data**(또는 접속 정보로 로컬 `mysql` 클라이언트 연결) 순서로 진행:

1. **중복 이메일 먼저 확인** (있으면 아래 `ALTER TABLE`이 바로 실패함):
   ```sql
   SELECT email, COUNT(*) FROM member GROUP BY email HAVING COUNT(*) > 1;
   ```
   결과가 없어야(빈 결과) 다음 단계로 진행 가능. 만약 중복이 있으면 어느 계정을 남길지부터 판단해야 함(이 프로젝트는 현재 실사용자가 없어서 발생 가능성은 낮지만, 스킵하지 말고 반드시 먼저 확인).
2. **인덱스 적용**:
   ```sql
   ALTER TABLE member ADD UNIQUE INDEX uk_member_email (email);
   ```
3. **확인**:
   ```sql
   SHOW INDEX FROM member WHERE Key_name = 'uk_member_email';
   ```
   결과가 1행 나오면 정상 적용된 것.

### 9-3. 기존 가입 계정의 `email_verified` 상태

이 배포 이전에 가입한 계정은 전부 `email_verified = false`(컬럼 기본값)로 시작한다 — 즉 이 기능 배포 이후엔 **기존 계정도 로그인이 막힌다**(인증 메일을 다시 받아야 함). 실사용자가 없는 개발용 프로젝트라 별도 백필 조치는 하지 않음 — 실사용자가 생긴 뒤에 이 기능을 배포하는 상황이라면, 배포 직전에 기존 row를 `UPDATE member SET email_verified = true WHERE created_at < '<배포 시각>'` 같은 방식으로 백필해야 기존 사용자가 갑자기 로그인이 막히는 걸 피할 수 있다(정직하게 남기는 메모 — 지금은 필요 없어서 안 함).

## 10. 반복되는 OOM 크래시 대응 — JVM 메모리 옵션 (2026-08-12)

프로덕션이 재시작 후 6~7분 간격으로 반복해서 메모리 부족(OOM)으로 크래시나는 걸 실제로 겪었다(Railway가 "Deploy Ran Out of Memory" 메일을 3회 연속 보냄, Metrics 탭에서 메모리가 플랜 한도 1GB를 넘어 1.5GB까지 튀는 것도 실제 그래프로 확인) — 상세 경위·실측 증거는 `docs/logs/cd/deploy/003-oom-crash.md` 참고.

**원인**: `Dockerfile`에 JVM 메모리 옵션이 전혀 없어서, 컨테이너 메모리 한도(1GB)를 JVM이 명시적으로 모르는 채로 돌고 있었다. **조치**: `ENTRYPOINT`에 `-XX:MaxRAMPercentage=70.0 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError` 추가 — 로컬에서 `docker run --memory=1g`로 동일한 한도를 걸어 재현 검증함(부팅 445MB, 90초 후 461MB로 안정).

**Railway 요금제 메모리 한도는 이미 최대치**(Settings → Resources, 1GB가 현재 플랜 상한, 슬라이더로 더 못 늘림 — "Upgrade for higher limits"만 있음)라는 것도 이번에 확인함. 이 JVM 옵션 조치로도 재발하면, 남은 선택지는 (a) 유료 플랜 전환으로 메모리 한도 자체를 올리거나 (b) heap dump 등으로 정확한 leak 소스를 특정해서 코드를 고치는 것뿐이다.

## 11. 로그인 고도화 3단계 — 카카오 로그인 배포 준비 (2026-08-12)

`docs/dev/auth/social-login/design.md` 기능을 실제로 쓰려면 카카오 개발자 콘솔 설정이 **직접** 필요함 — 코드만 배포해서는 카카오 로그인 버튼을 눌러도 실패한다.

### 11-1. 카카오 개발자 콘솔 앱 등록

1. [developers.kakao.com](https://developers.kakao.com)에서 카카오 계정으로 로그인 → **내 애플리케이션** → **애플리케이션 추가하기**(앱 이름 아무거나, 예: "GONG9RI")
2. 앱 선택 → **앱 키** 메뉴에서 **REST API 키** 확인(이게 `KAKAO_CLIENT_ID`)
3. **카카오 로그인** 메뉴 → 활성화 설정 **ON**
4. 같은 메뉴의 **Redirect URI**에 아래 2개 등록(로컬 개발용 + 프로덕션용 둘 다):
   ```
   http://localhost:8080/api/auth/kakao/callback
   https://gong9ri-production.up.railway.app/api/auth/kakao/callback
   ```
5. **동의항목** 메뉴에서 "닉네임"은 기본 제공, **"카카오계정(이메일)"**을 원하면 "선택 동의"로 설정 — 개인 개발자 앱은 콘솔에 등록한 테스트 사용자 계정에 한해서만 이메일이 실제로 오고, 불특정 다수에게 받으려면 카카오 비즈 심사가 필요할 수 있음(실제 화면에서 직접 확인, 여기서 추측 안 함).
6. (선택) **보안** 메뉴에서 "Client Secret"을 발급해 활성화했다면 그 값이 `KAKAO_CLIENT_SECRET` — 활성화 안 했으면 이 환경변수는 빈 값으로 둬도 됨.

### 11-2. Railway 환경변수 추가

앱 서비스 → **Variables** 탭에서 추가:

```
KAKAO_CLIENT_ID=<11-1에서 확인한 REST API 키>
KAKAO_CLIENT_SECRET=<Client Secret 활성화했으면 그 값, 아니면 비워둠>
```

> `APP_BASE_URL`은 이미 로그인 고도화 2단계에서 설정해뒀다면 그대로 재사용된다(카카오 콜백 리다이렉트 URI를 이 값 + `/api/auth/kakao/callback`으로 조합함) — 안 돼있으면 9-1절 참고해서 같이 설정.

## 12. 회원번호·상품코드·주문번호·공구팀 번호 배포 절차 (admin-identifier-codes, 2026-08-22)

`docs/policy/identifier-code.md`(포맷·채번 규칙)·`docs/dev/ongoing/admin-identifier-codes.md`(계획)
기능을 실제로 반영하려면 **배포를 2단계로 나눠야 한다.** `member.email` UNIQUE 인덱스 사례(9-2)와
근본 원인은 같지만(기존 데이터가 있는 컬럼에 제약을 거는 문제), 이번엔 컬럼 자체가 새로 생기고
게다가 **행마다 값이 달라서** 상수 `DEFAULT`로 채울 수 없다는 점이 다르다 — 그래서 "인덱스만 수동으로
건다"가 아니라 "값부터 채우고 나서 제약을 건다"는 순서가 필요하다.

### 12-1. 1단계 배포 — nullable 컬럼 추가 + 백필

이번 Generate 라운드가 반영한 상태가 이 1단계다: `member.member_code`/`product.product_code`/
`payment.order_no`/`group_buy_team.team_no` 4개 컬럼이 전부 **nullable**로 추가돼 있고, 신규
가입/상품등록/결제생성/팀신설은 저장 직후 자동으로 채번한다. 기존 행(이 컬럼이 생기기 이전 데이터)은
값이 없다.

1. 이 코드를 배포한다(`ddl-auto: update`가 nullable 컬럼 추가는 기존 데이터가 있어도 안전하게
   처리한다 — `member.kakao_id`가 "브랜드 뉴 컬럼"으로 문제없이 추가된 사례와 동일, `docs/db/
   member.md` "마이그레이션 메모" 참고).
2. 배포 환경(Railway 앱 서비스 → Variables)에 `APP_BACKFILL_IDENTIFIER_CODE=true`(또는
   `application.yaml`이 읽는 이름 그대로 `app.backfill.identifier-code=true`)를 **한 번** 추가하고
   재배포(재기동)한다 — `IdentifierCodeBackfillRunner`가 기동 시 1회 `IdentifierCodeBackfillService`로
   4개 테이블의 기존 행을 전부 채운다. 배포 로그에 `식별 코드 백필 실행기 종료: 총갱신행수=N`이
   찍히는지 확인한다.
3. **백필이 끝났으면 그 환경변수를 즉시 제거하거나 `false`로 되돌린다** — 안 그러면 재기동마다
   불필요하게 다시 스캔한다(대상이 없으면 스캔만 하고 아무 것도 안 바꾸므로 데이터 안전성 문제는
   없지만, 매 기동 스캔은 낭비다).
4. 아래 SQL로 4개 테이블 모두 NULL이 0건인지 직접 확인한다(다음 단계로 넘어가기 전 필수):
   ```sql
   SELECT COUNT(*) FROM member WHERE member_code IS NULL;
   SELECT COUNT(*) FROM product WHERE product_code IS NULL;
   SELECT COUNT(*) FROM payment WHERE order_no IS NULL;
   SELECT COUNT(*) FROM group_buy_team WHERE team_no IS NULL;
   ```
   전부 0이어야 다음 단계(제약 적용)로 넘어갈 수 있다. 0이 아니면(백필 실행기가 안 돌았거나 실패)
   재실행한다.

### 12-2. 2단계 배포 — NOT NULL + UNIQUE 제약 적용 (백필 완료 확인 후에만)

12-1의 NULL 카운트가 4개 테이블 전부 0임을 확인한 뒤에만 진행한다.

1. 4개 엔티티(`Member.memberCode`, `Product.productCode`, `Payment.orderNo`,
   `GroupBuyTeam.teamNo`)의 `@Column` 애노테이션을 `nullable = false, unique = true`로 바꾼다.
2. 재배포한다 — 이 시점엔 4개 컬럼 모두 값이 채워져 있으므로 `ddl-auto: update`가 컬럼 제약
   변경(`MODIFY COLUMN ... NOT NULL` + `ADD UNIQUE INDEX`)을 안전하게 적용할 수 있다.
3. 배포 후 아래로 실제 제약이 걸렸는지 확인한다:
   ```sql
   SHOW INDEX FROM member WHERE Key_name LIKE '%member_code%';
   SHOW INDEX FROM product WHERE Key_name LIKE '%product_code%';
   SHOW INDEX FROM payment WHERE Key_name LIKE '%order_no%';
   SHOW INDEX FROM group_buy_team WHERE Key_name LIKE '%team_no%';
   ```
   각각 결과가 1행 이상 나오면 정상 적용된 것.

> **아직 이 저장소 코드는 1단계 상태다.** 2단계(제약 적용)는 위 확인 절차를 실제로 밟은 뒤 별도
> 작업으로 진행한다 — 지금 코드에 그대로 `nullable = false`를 적용하면, 이미 데이터가 있는 로컬
> dev DB나 배포 DB에서 컬럼 추가 자체가 실패할 수 있다(`docs/policy/identifier-code.md` "마이그레이션"
> 절 참고).

## 참고 — 확인 안 된 것

- Railway 무료/저가 플랜의 실제 정책(월 크레딧 한도, 미사용 시 슬립 여부 등)은 가입 후 요금제 화면에서 직접 확인할 것 — 여기서 추측해서 적지 않음.

---

## 호스팅 이전 런북 (2026-08-21 실제 수행)

무료 크레딧 소진이 임박해 **다른 워크스페이스의 새 프로젝트로 서비스를 통째로 옮겼다.** 같은 일을 다시 하게 될 때를 위해 실제로 밟은 순서와 함정을 남긴다.

> 옮기는 동안 **기존 서비스는 절대 내리지 않는다.** 새 쪽이 완전히 확인될 때까지 롤백 경로로 살려둔다.

### 1) 새 프로젝트 준비 — 서비스 이름이 계약이다

새 워크스페이스에 프로젝트를 만들고 **MySQL·Redis를 앱보다 먼저** 추가한다. 이때 **이름을 기존과 똑같이** 짓는다(`MySQL`, `Redis` — 대소문자까지).

환경변수가 값이 아니라 **참조**로 돼 있기 때문이다.

```
DB_URL="jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?..."
```

이름이 다르면 참조가 해석되지 않아 앱이 뜨지 않는다.

> **CLI로 환경변수를 뽑으면 안 된다.** `railway variables --json`은 참조를 **이미 값으로 풀어서** 준다 — 그걸 새 프로젝트에 넣으면 **옛 DB를 가리킨다.** 대시보드의 **Raw Editor**를 그대로 복사해야 참조가 참조인 채로 넘어간다.

이후 앱 서비스에 GitHub 레포를 연결하고, **볼륨을 `/data`에 마운트**한다(`UPLOAD_DIR=/data/uploads`).

### 2) 주소 관련 값

| 값 | 처리 |
|---|---|
| `APP_BASE_URL` | 새 주소로. **끝에 슬래시를 붙이지 않는다** — 지금은 `AppUrlProperties`가 떼어내지만, 원래 이 값으로 카카오 콜백 주소를 만들어서 슬래시 하나로 로그인이 깨질 수 있었다 |
| `robots.txt`·sitemap·canonical | **손댈 것 없다.** 전부 `app.base-url`이나 접속 주소를 따라가게 바꿔뒀다 |

### 3) DB 이사 — 임시 TCP 프록시

두 프로젝트의 MySQL 모두 내부 전용이라, **임시로 공개 프록시를 열고 끝나면 반드시 닫는다.**

```bash
railway tcp-proxy create -s MySQL --port 3306      # 양쪽 모두
mysqldump -h <host> -P <port> -uroot -p<pw> \
  --single-transaction --set-gtid-purged=OFF --no-tablespaces \
  --databases railway > dump.sql
mysql -h <새host> -P <새port> -uroot -p<pw> < dump.sql
railway tcp-proxy delete -s MySQL <id> --yes       # 양쪽 모두, 잊지 말 것
```

- **데드락이 한 번 났다** — 라이브 앱이 동시에 쓰고 있어서다. 재시도하면 된다(`--single-transaction`이라 스냅샷 일관성은 유지된다).
- 복원 후 **테이블별 건수를 원본과 대조**한다. "복원 성공"만으로는 부족하다.

### 4) 업로드 이미지 이사

DB에서 실제 참조되는 파일 목록을 뽑고, **새 컨테이너가 옛 사이트에서 직접 받아오게** 한다(로컬을 거치지 않는다).

```bash
# 옮길 목록
SELECT url FROM product_image WHERE url LIKE '/uploads/%'
UNION SELECT image_url FROM product WHERE image_url LIKE '/uploads/%';

# 새 컨테이너에서
railway ssh -s GONG9RI -- sh -c 'curl -sf -o /data/uploads/... <옛주소>/uploads/...'
```

옮긴 뒤 새 주소로 실제 200이 나오는지, **크기가 원본과 같은지** 확인한다.

### 5) 캐시 비우기 — 이거 안 하면 "이사가 안 된 것처럼" 보인다

복원 직후 상품 목록이 **0개로 나왔다.** DB에는 37개가 있는데도 그랬다. 원인은 Redis에 **"비어있음"이 캐시**돼 있었기 때문이다(복원 전에 조회한 결과, TTL 30분).

```
productList::0-20-ALL-NONE-false   ← 이런 키를 지운다
productDetail::4
```

세션·인증 토큰 키는 건드리지 않는다.

### 6) 외부 콘솔 — 앱을 새로 만들지 않는다

**기존 앱에 새 주소를 한 줄씩 추가**하면 된다. 기존 주소는 **지우지 않는다**(롤백용).

| 콘솔 | 위치 |
|---|---|
| 카카오 — JS SDK 도메인 | 앱 설정 → 플랫폼 키 → JavaScript 키 |
| 카카오 — 웹 도메인 | 앱 → **제품 링크 관리** ← 위와 다른 화면이다. 이걸 빠뜨리면 카톡 카드에서 **링크만 조용히 제거**된다 |
| 카카오 — 로그인 Redirect URI | 앱 설정 → 플랫폼 키 → REST API 키. **경로까지** 넣어야 한다 |
| PortOne — 웹훅 주소 | 새 주소로 변경 |
| Google Search Console | 새 주소를 속성으로 추가. 인증 파일이 레포에 있어 자동 통과 |

### 7) 확인 순서

1. `/actuator/health`, `/api/products`, `/robots.txt`(사이트맵 주소가 새 주소인지)
2. 인증 필요한 경로가 401을 주는지, WebSocket 핸드셰이크가 비로그인 401인지
3. 기존 계정으로 로그인(비밀번호는 암호화된 채 이관되므로 그대로 된다)
4. **카톡 공유 카드에 버튼이 붙는지** — 붙지 않으면 6번의 웹 도메인 등록을 다시 본다
5. 결제·상담 등 외부 연동

