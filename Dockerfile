# 1단계: 빌드 — gradlew로 bootJar까지 (QueryDSL 애노테이션 프로세싱은 bootJar 안에서 처리됨)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어를 소스 변경과 분리해 캐싱되게 함
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 — JDK 없이 JRE만으로 실행
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
# 2026-08-14 원인 확정(1차) — 반복되는 프로덕션 OOM 크래시(2026-08-12부터, docs/logs/cd/deploy/003-oom-crash.md)의
# 원인 중 하나는 컨테이너 메모리가 부족해서가 아니라, **JVM의 컨테이너 메모리 자동 감지(UseContainerSupport)가
# Railway 환경에서 고장나 있었던 것**이다. `railway ssh`로 실행 중인 컨테이너에 직접 들어가 실측 확인함:
# `/sys/fs/cgroup/memory.max`(실제 컨테이너 한도) = 999997740 bytes(≈954MB)인데, 그 상태에서 뜬 JVM이
# `-XX:MaxRAMPercentage=60.0`으로 계산한 MaxHeapSize는 **32178700288 bytes(≈30GB)** — 실제 한도의 30배가
# 넘는 값이었다. JVM이 스스로는 메모리가 남아돈다고 착각하니 `OutOfMemoryError`를 한 번도 자체 감지하지
# 못했고, 실제 954MB 한도에 부딪히는 순간 리눅스가 예고 없이 컨테이너를 죽였다.
# 대응: 자동 감지(퍼센트 기반)를 신뢰하지 않고 **힙 크기를 고정값으로 직접 지정**한다 — 실제 한도(954MB)
# 안에서 힙 512MB + 메타스페이스 192MB + 나머지 여유 ~250MB로 배분.
#
# 2026-08-19 원인 확정(2차, Attempt 5) — 힙을 고정했는데도 재발해서 `railway ssh`로 다시 실측(JDK 이미지로
# 잠깐 바꿔 jstack/`-XshowSettings:system` 사용). 컨테이너의 "Effective CPU Count"가 **48**로 잡히는 걸
# 확인함(Railway가 실제로 주는 건 2 vCPU — CPU Quota 200000us / Period 100000us = 2.0로 직접 계산 확인) —
# 메모리와 완전히 같은 종류의 컨테이너 인식 버그가 CPU 쪽에도 있었던 것. `Runtime.availableProcessors()`가
# 48을 반환하면서, Spring의 WebSocket 메시지 브로커 기본 스케줄러(`AbstractMessageBrokerConfiguration
# .messageBrokerTaskScheduler()`, 바이트코드로 직접 확인 — `setPoolSize(Runtime.getRuntime()
# .availableProcessors())`)가 스레드풀 크기를 48로 잡아, 프로덕션에서 시간이 지날수록 "MessageBroker-N"
# 스레드가 최대 48개까지 계속 늘어났다(스레드당 -Xss512k 스택 + malloc arena 오버헤드로 non-heap을
# 크게 잠식) — 이게 힙 고정만으로는 못 막은 반복 재발의 실제 원인. CPU 코어 수로 풀 크기를 정하는 건 이
# 라이브러리 하나만의 특성이 아니라 Netty/Reactor/ForkJoinPool 등 여러 곳의 공통 관행이라, 근본적으로는
# JVM이 인식하는 프로세서 수 자체를 고정하는 게 맞다고 판단.
# 대응: `-XX:ActiveProcessorCount=2`로 JVM이 인식하는 코어 수를 실제 한도에 맞게 고정(Effective CPU Count
# 오감지와 무관하게 Runtime.availableProcessors()가 정확히 2를 반환하게 강제) — CPU 코어 수 기반으로
# 스레드풀을 자동으로 잡는 모든 컴포넌트에 한 번에 적용되는 근본 대응이라, Spring 메시지 브로커 스케줄러를
# 개별적으로 오버라이드하는 것보다 우선한다(WebSocketConfig.java의 명시적 풀 크기 지정은 그대로 유지 —
# 방어적으로 이중 안전장치).
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=192m", "-Xss512k", "-XX:ActiveProcessorCount=2", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
