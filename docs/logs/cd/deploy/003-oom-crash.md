# 003-oom-crash — 프로덕션 반복 OOM 크래시 (로그)

## Attempt 1 — 2026-08-12 (실제 장애 대응 중 발견)

- **증상**: 로그인 고도화 2단계(이메일 인증/비밀번호 재설정) 배포 + 팀원의 결제(PortOne) 배포가 짧은 시간 안에 연달아 이루어진 뒤, Railway로부터 "Deploy Ran Out of Memory! ... ran out of memory within the production environment and crashed." 알림 메일을 **3회** 연속으로 받음. 팀원이 회원가입 인증 메일을 못 받는다고 보고, 이후 `POST /api/auth/verify-email/resend` 호출 중 실제로 `502 Application failed to respond` 발생.
- **실측 확인**:
  - Railway Metrics 탭: CPU가 순간적으로 2.0 vCPU(플랜 한도)까지, 메모리가 1.5GB(플랜 한도 1GB를 50% 초과)까지 튀는 구간을 실제 그래프로 확인. 평소 baseline도 이미 825MB로 1GB 한도 대비 여유가 크지 않았음.
  - Deploy Logs(HTTP 로그) 타임라인 대조: 22:22:11 재시작 완료(헬스체크 통과, 정상) → 22:26:41까지 정상 200 응답 → **22:28:39에 크래시**(그 순간 처리 중이던 `/verify-email/resend` 요청이 502로 실패) → 이후 모든 요청이 즉시 502(프로세스 자체가 죽은 패턴, 특정 엔드포인트의 로직 버그라기보다 그 순간 우연히 걸린 요청) → 22:30:25에 재시작(RAG 색인 재실행 로그로 확인).
  - **패턴**: 재시작 완료 후 약 6~7분 뒤 다시 크래시 — 특정 요청이 트리거가 아니라 시간 경과에 따라 메모리가 누적되다 한도를 넘기는 패턴으로 판단.
  - Railway "Replica Limits" 설정(Settings 탭) 확인: CPU 2 vCPU / 메모리 1GB가 **이미 현재 요금제의 최대치**(슬라이더가 한도까지 꽉 차 있음, "Upgrade for higher limits" 링크만 있고 더 못 늘림) — 무료로 늘릴 방법 없음, 유료 전환만 대안.
- **원인 추정**: `Dockerfile`의 `ENTRYPOINT`에 JVM 메모리 관련 옵션이 **전혀 없었다**(`-Xmx`, `-XX:MaxRAMPercentage` 등 전무) — 컨테이너 메모리 한도(cgroup, 1GB)를 JVM에 명시적으로 알려주지 않고 JDK 17의 기본 컨테이너 인식 로직(`UseContainerSupport`)에만 의존한 상태였다. 힙 자체는 기본값(컨테이너 메모리의 약 25%)으로 제한돼도, 메타스페이스·스레드 스택·다이렉트 버퍼 등 비힙 영역은 별도 상한이 없어 시간이 지나며 예측 불가능하게 자랄 여지가 있었다. `AsyncConfig`의 스레드풀은 명시적으로 상한(core 2/max 10)이 있어 배제, `PolicyDocumentIndexer`는 문서 2개짜리 색인이라 이 정도 스파이크의 원인으로 보기엔 규모가 너무 작아 배제 — 정확한 leak 소스를 heap dump 등으로 확증하진 못했다(프로덕션에 exec/프로파일링 접근 수단이 없음, 정직하게 남김).
- **조치**: `Dockerfile`의 `ENTRYPOINT`에 JVM 옵션 3개 추가.
  ```
  ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:MaxMetaspaceSize=192m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
  ```
  - `MaxRAMPercentage=70.0`: 힙 상한을 컨테이너 메모리의 70%로 명시(나머지 30%는 스레드 스택·네이티브 버퍼·JVM 자체 오버헤드용 여유).
  - `MaxMetaspaceSize=192m`: 메타스페이스가 무제한으로 자라는 것을 방지.
  - `+ExitOnOutOfMemoryError`: OOM이 실제로 발생하면 JVM이 애매하게 남아있지 않고 즉시 종료해서 Railway가 빠르고 깔끔하게 재시작을 트리거하게 함(느린 죽음보다 빠른 재시작이 낫다는 판단).
  - 세 값 모두 **실측 근거 없는 초기값**이다(이 프로젝트의 다른 임계값들과 같은 성격) — 재발 시 Railway Metrics로 실제 사용량을 보고 조정 필요.
- **로컬 검증**: 수정한 `Dockerfile`로 `docker build` + `docker run --memory=1g`(프로덕션과 동일한 1GB 한도)로 실제 기동해서 확인.
  - 정상 기동, `/actuator/health` 200.
  - 부팅 직후 메모리: **445MB**(프로덕션에서 관찰된 기존 baseline 825MB보다 훨씬 낮음).
  - 가벼운 트래픽(헬스체크·상품조회 반복 호출) 후 90초 경과 시점: **461MB**(완만한 증가, 워밍업 수준 — 폭주 없음).
- **미해결/후속 필요**: 이 조치가 실제 프로덕션 재발을 막는지는 배포 후 최소 수 시간~하루 단위로 Railway Metrics를 지켜봐야 확정할 수 있다(오늘 로컬 90초 검증만으로는 "6~7분 뒤 재발" 패턴이 실제로 사라졌는지 완전히 증명 못 함). 재발하면 (a) `MaxRAMPercentage`를 더 낮추거나, (b) Railway 유료 플랜으로 메모리 한도 자체를 올리거나, (c) heap dump를 뜰 수 있는 방법(Railway exec 지원 여부 확인 등)을 찾아 실제 leak 소스를 특정해야 한다.

## Attempt 2 — 2026-08-13 (팀원, `d495e31`) ❌ 결과적으로 효과 없었음 (Attempt 3에서 밝혀짐)

- **증상**: Attempt 1 배포 후에도 재발. Railway Metrics 실측: 조용한 상태에서도 baseline이 이미 997MB/1000MB, 요청이 들어오면 1.7~1.8GB까지 튐 — Attempt 1의 `MaxRAMPercentage=70`만으로는 부족하다고 판단.
- **원인 추정**: JVM 옵션이 하나도 없었던 최초 상태(힙 기본값 = 컨테이너 메모리의 약 25%, ≈250MB)에서도 baseline이 825MB였다는 Attempt 1의 기록을 다시 보면, 애초에 힙보다 **non-heap**(힙 바깥 영역) 쪽이 더 크다는 뜻이라 힙만 줄이는 건 근본 대응이 아니라고 판단. 톰캣 기본 스레드풀(200개, 스레드당 기본 스택 ~1MB → 최악의 경우 200MB)을 non-heap의 유력 용의자로 지목.
- **조치**:
  - `server.tomcat.threads.max: 50` 추가 (`src/main/resources/application.yaml`) — 기존 200개 기본값을 부트캠프 규모 실측 부하테스트(k6, VU 2970 근처에서야 무너짐, `docs/logs/team/crud/004-spike-test.md`) 기준으로 축소.
  - `Dockerfile` ENTRYPOINT에 `-Xss512k` 추가(스레드당 스택 축소), `MaxRAMPercentage`는 70 → 60으로 소폭 하향(힙을 과하게 굶기지 않으면서 non-heap에 여유를 더 줌).
- **후속(Attempt 3에서 확인)**: 이 조치 자체(스레드 수·스택 축소)는 non-heap 점유를 줄이는 방향으로는 유효했지만, `MaxRAMPercentage`가 애초에 컨테이너의 실제 메모리 한도를 전혀 못 읽고 있었다는 게 나중에 밝혀지면서 **퍼센트 기반 힙 상한 자체가 사실상 무제한이었다** — 즉 이 Attempt의 핵심 조정(`MaxRAMPercentage` 70→60)은 원인 진단이 틀려서 실질적인 효과가 없었다(스레드풀 축소는 별개로 유효한 개선이라 유지됨).

## Attempt 3 — 2026-08-14 (팀원, `66de5af`) ✅ 진짜 원인 확정

- **증상**: Attempt 2 배포 후에도 재발. 크래시 직전 로그에 자바 레벨 에러(OOM 등)가 단 한 줄도 없다는 점이 Attempt 1부터 계속 이상했음.
- **실측 확인**: `railway ssh`로 실행 중인 프로덕션 컨테이너에 직접 접속해 실측.
  - `/sys/fs/cgroup/memory.max`(실제 컨테이너 한도) = 999997740 bytes (≈954MB).
  - 그런데 그 상태에서 뜬 JVM이 `-XX:MaxRAMPercentage=60.0`으로 계산한 `MaxHeapSize`는 **32178700288 bytes(≈30GB)** — 실제 한도의 30배가 넘는 값.
- **원인 확정**: JDK의 컨테이너 메모리 자동 감지(`UseContainerSupport`)가 Railway 환경에서 실제 cgroup 한도를 전혀 읽지 못하고 있었다. 그래서 Attempt 1·2에서 조정한 `MaxRAMPercentage` 값(70 → 60)은 전부 "30GB의 몇 %"로 계산되어 사실상 무제한이었던 것 — 두 시도 모두 핵심 원인을 못 건드리고 있었다. JVM은 스스로 메모리가 남아돈다고 착각해 `OutOfMemoryError`를 한 번도 자체 감지하지 못했고, 실제 954MB 한도를 넘는 순간 리눅스가 예고 없이 컨테이너를 강제 종료했다 — 이것이 크래시 직전 로그에 자바 레벨 에러가 전혀 없었던 이유.
- **조치**: `Dockerfile` ENTRYPOINT에서 퍼센트 기반 설정을 버리고 힙 크기를 **고정값**으로 직접 지정.
  ```
  ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=192m", "-Xss512k", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
  ```
  - 실제 한도(954MB) 안에서 힙 512MB + 메타스페이스 192MB + 나머지(스레드 스택·다이렉트 버퍼·네이티브 등) 약 250MB로 배분(안전 마진 포함, cgroup 실측값 기준).
  - Attempt 2의 `-Xss512k`(스레드당 스택 축소)는 그대로 유지.
- **로컬 검증**: `docker run --memory=954m`(실제 프로덕션과 동일 한도)로 5분간 재현 테스트 — `/actuator/health` 200 유지, RSS 508MB → 509MB로 사실상 정체, 크래시 없음.
- **미해결/후속 필요**: 로컬 5분 검증은 통과했지만, Attempt 1도 로컬 90초 검증 시점엔 문제없어 보였다가 프로덕션에서 재발한 전례가 있다 — 이번에도 프로덕션 배포 후 최소 수 시간~하루 단위로 Railway Metrics를 실측 확인하기 전까지는 "해결됐다"고 단정하지 않는다. 재발 시 힙 512MB 자체가 너무 타이트할 가능성(GC 압박 증가)도 함께 점검 필요.

## Attempt 4 — 2026-08-19 (WebSocket 채널 스레드풀 무제한) ✅ 실측으로 원인 특정

- **증상**: Attempt 3 배포(고정 힙 512MB) 이후 며칠은 안정적이었으나, 이번 세션에서 기능 배포를
  여러 번 연달아 하는 동안 다시 재발 — 약 1시간 간격으로 새 배포 없이 재시작(`Started
  Gong9riApplication` 로그만 찍히고 그 직전 자바 레벨 에러는 없음, Attempt 1~3과 같은 패턴).
  `railway metrics --memory`로 확인한 24시간 평균이 이미 901MB(한도 1024MB의 88%)로 평소에도
  여유가 거의 없었고, 배포 전환 구간(무중단 배포로 신·구 컨테이너가 잠깐 같이 뜸)에서 1.7~1.8GB까지
  튀는 것도 반복 관찰.
- **실측 확인**: `railway ssh`로 실행 중인 프로덕션 컨테이너에 직접 접속(Attempt 3과 동일 방법).
  - `/sys/fs/cgroup/memory.current` = 913MB / `memory.max` = 954MB(91%) — 조용한 상태에서도 이미 임계치.
  - `ps aux`: 자바 프로세스 RSS 844MB, VSZ 10GB(가상 주소공간은 참고용, 실제 문제는 RSS).
  - `/sys/fs/cgroup/memory.stat`의 `anon`(익명 메모리, 힙+네이티브) = 827MB — Dockerfile의
    `-Xmx512m -XX:MaxMetaspaceSize=192m`(합 704MB) 상한을 이미 100MB 이상 넘어서 있음. 즉 이번엔
    힙이 아니라 **non-heap(스레드 스택 등 네이티브 메모리)가 원인**이라는 뜻(Attempt 2의 가설과
    같은 방향, 그때는 원인 진단 자체가 틀렸었지만 이번엔 실측으로 확인).
  - `/proc/1/status`의 `Threads` = **140개**. `/proc/1/task/*/comm`을 전부 뽑아 이름별로 집계해보니
    `MessageBroker-1`~`MessageBroker-4`가 각각 11개씩(총 48개, 전체 스레드의 34%) 중복 존재 —
    정상이라면 이름당 1개여야 한다. `WebSocketConfig`(`registry.enableSimpleBroker("/topic")`,
    `configureClientInboundChannel`/`configureClientOutboundChannel` 전부 미설정)가 브로커용
    태스크 스케줄러·클라이언트 인바운드/아웃바운드 채널 executor 크기를 Spring 기본값(사실상
    무제한에 가까운 max)에 맡기고 있었던 게 원인으로 확정 — 스레드 하나당 `-Xss512k` 스택 예약 +
    glibc malloc arena 오버헤드가 붙어, 이 정도 개수만으로도 non-heap을 수백MB 단위로 잠식한다.
    (참고: `GET /ws-team -> 403`이 ~10초 간격으로 계속 로그에 찍히는 게 이 세션 내내 관찰됐는데,
    브라우저 탭이 열린 채 세션 없이 계속 재연결을 시도하는 클라이언트로 추정 — 이런 연결 시도가
    쌓이면서 채널 executor가 계속 성장했을 가능성이 있으나, 정확한 트리거까지는 100% 확증 못 함.
    JRE 전용 런타임 이미지라 `jcmd`/`jstack` 등 표준 진단 도구가 없어 스레드 이름 집계 이상의
    스택트레이스 레벨 확증은 불가능했음 — 정직하게 남김.)
- **조치**: `WebSocketConfig.java`에 명시적으로 작은 고정 크기를 못박음(이 앱 규모 — 2인팀, 단일
  인스턴스, 팀 정원 변경 브로드캐스트뿐 — 에 맞춤, 실측 근거 없는 초기값):
  - 브로커 태스크 스케줄러: `ThreadPoolTaskScheduler` 직접 생성, `poolSize=2`, `setTaskScheduler()`로
    브로커에 명시 주입.
  - `configureClientInboundChannel`/`configureClientOutboundChannel`: 각각 `corePoolSize=2,
    maxPoolSize=4`로 제한.
- **로컬 검증**: `./gradlew test` 356개 전체 통과(회귀 없음). `bootRun`으로 기동해 WebSocket 연결 1건
  실측(`WebSocketMessageBrokerStats` 로그로 `inboundChannel`/`outboundChannel` pool이 idle 시
  `pool size = 0`으로 정상 대기 상태인 것 확인, 필요시에만 core=2까지 늘어나고 max=4를 넘지 않음).
- **미해결/후속 필요**: 배포 후 실측한 결과 Attempt 4로는 부족했다 — 아래 Attempt 5에서 진짜 원인이
  하나 더 있었음이 밝혀짐.

## Attempt 5 — 2026-08-19 (같은 날, JDK 이미지로 임시 전환해 jstack 실측) ✅ 근본 원인 확정

- **증상**: Attempt 4 배포 후에도 재발 — 배포 47분 뒤 다시 확인하니 `MessageBroker-1`(11개)/
  `MessageBroker-2`(5개)가 재등장(내가 새로 만든 `ws-broker-1`/`ws-broker-2`는 정상적으로 1개씩만
  유지되고 있었음, 그건 확실히 고쳐짐). 즉 Attempt 4의 조치(`WebSocketConfig`에서 명시적으로 만든
  스케줄러)와는 **별개의 스케줄러**가 여전히 기본값으로 남아 계속 자라고 있었다는 뜻.
- **실측 확인**: JRE 전용 이미지라 `jcmd`/`jstack`이 없어 막혀있던 걸, `Dockerfile`을 임시로
  `eclipse-temurin:17-jdk-jammy`로 바꿔 재배포한 뒤 `railway ssh`로 직접 진단.
  - `jstack 1`로 스레드 덤프를 여러 번 떠서 비교: `MessageBroker-N` 스레드가 이름 중복 없이(각기
    다른 tid) 시간이 지나며 -1→-7까지 순차적으로 계속 늘어나는 걸 확인(부팅 후 190초 시점에 이미
    7개) — 요청 하나당 하나씩 생기는 게 아니라 **시간 기반으로 계속 새 워커 스레드가 추가되는 패턴**
    (실제로 curl로 `/ws-team`에 정상적인 WebSocket 업그레이드 헤더를 보내 프로덕션의
    `GET /ws-team -> 403` 로그를 그대로 재현하는 데는 성공했지만, 그 요청 전후로 스레드 수가
    1:1로 늘어나진 않아 "매 연결 시도마다 스레드 하나"라는 가설은 기각).
  - `spring-messaging-7.0.8.jar`의 `AbstractMessageBrokerConfiguration.class`를 `javap -c`로
    직접 디컴파일해서 바이트코드 확인: `messageBrokerTaskScheduler()` 빈이
    `new ThreadPoolTaskScheduler()` → `setThreadNamePrefix("MessageBroker-")` →
    `setPoolSize(Runtime.getRuntime().availableProcessors())` 순서로 만들어짐을 직접 확인 —
    **풀 크기를 JVM이 인식하는 CPU 코어 수로 잡고 있었다.** 이 빈은 `WebSocketConfig`에서
    `registry.enableSimpleBroker().setTaskScheduler(...)`로 내가 만든 스케줄러를 넘겨준 것과
    무관하게 Spring이 `@EnableWebSocketMessageBroker` 하나로 자동 생성하는 별도 빈이라, Attempt 4의
    조치로는 건드리지 못했던 것.
  - `railway ssh -- java -XshowSettings:system -version`으로 컨테이너가 인식하는 CPU 정보를
    직접 확인: **`Effective CPU Count: 48`**(호스트 물리 코어 수 그대로) — 그런데 같은 출력의
    `CPU Quota: 200000us` / `CPU Period: 100000us`를 계산하면 실제 한도는 **2.0**(Railway 요금제의
    2 vCPU와 정확히 일치). 즉 `Runtime.availableProcessors()`가 48을 반환하고 있었다는 뜻 —
    Attempt 3에서 확정한 메모리 자동 감지 오류(30GB로 착각)와 **완전히 같은 종류의 컨테이너 인식
    버그가 CPU 쪽에도 있었다.** `messageBrokerTaskScheduler`가 풀 크기를 48로 잡고, 시간이 지나며
    브로커 내부 주기 작업(세션 정리·하트비트 등)이 새 워커를 계속 소비하면서 48개까지 서서히
    쌓이는 것 — 스레드 하나당 `-Xss512k` 스택 + malloc arena 오버헤드가 붙어 non-heap을 크게
    잠식한다. 이게 Attempt 4까지의 조치로 못 막았던 진짜 근본 원인이었다.
  - CPU 코어 수 기반 자동 사이징은 Spring의 이 스케줄러 하나만의 관행이 아니라 Netty 이벤트루프,
    Reactor의 `Schedulers.parallel()`, `ForkJoinPool.commonPool()`(GC/스트림 병렬 연산 등)에서도
    흔히 쓰는 패턴이라, 이 컨테이너에서 CPU 코어 수를 잘못 읽는 문제 자체가 다른 곳에서도 잠재적으로
    비슷한 과다 사이징을 일으키고 있었을 가능성이 있다(개별 확인은 못 함, 근본 대응으로 한 번에 해소).
- **조치**: `Dockerfile` ENTRYPOINT에 `-XX:ActiveProcessorCount=2` 추가 — JVM이 인식하는 프로세서
  수 자체를 실제 한도(2 vCPU)로 고정해서, `Runtime.availableProcessors()`를 참조하는 모든 컴포넌트가
  한 번에 정확한 값을 보게 한다(개별 라이브러리를 하나씩 찾아 오버라이드하는 것보다 근본적인 대응).
  Attempt 4의 `WebSocketConfig` 명시적 풀 크기 지정은 이중 안전장치로 그대로 유지. 진단용으로 잠깐
  바꿨던 런타임 이미지도 다시 `eclipse-temurin:17-jre-jammy`(JRE 전용)로 원복.
- **로컬 검증**: `./gradlew test` 356개 전체 통과(회귀 없음, 힙/CPU 플래그는 로컬 실행에 영향 없음).
- **미해결/후속 필요**: 이전 Attempt들과 마찬가지로 배포 후 최소 몇 시간(가능하면 하루) Railway
  Metrics + `railway ssh`로 스레드 수·메모리 추세를 실측 확인해야 확정된다. 재발하면 (a) 힙을 더
  줄이거나 Metaspace를 더 좁혀 non-heap 여유를 추가 확보, (b) `GET /ws-team -> 403` 반복 재연결의
  정확한 클라이언트를 찾아 근본 차단, (c) 유료 플랜으로 메모리/CPU 한도 자체를 상향.

### 참고: 관련이지만 별개인 조치 — 헬스체크 타임아웃 연장 (2026-08-13, `61641a6`)

Attempt 2~3 사이, 부팅 중 한 번 크래시 후 재시작하는 패턴이 있어 재시작 후 두 번째 시도가 기존 5분(300초) 헬스체크 예산 안에 못 끝나 배포 자체가 실패하는 문제가 있었다. `railway.json`의 `healthcheckTimeout`을 300 → 600초로 늘려 우선 배포부터 통과하게 함 — 메모리 원인 자체를 고친 조치는 아니고 배포 재시도 여유를 늘린 것.
