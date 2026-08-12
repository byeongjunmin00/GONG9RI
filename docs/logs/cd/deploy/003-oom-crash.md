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
