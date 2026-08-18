# 1단계: 빌드 — gradlew로 bootJar까지 (QueryDSL 애노테이션 프로세싱은 bootJar 안에서 처리됨)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어를 소스 변경과 분리해 캐싱되게 함
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 — 임시로 JDK 이미지 사용 중(jstack으로 반복 OOM 재발 원인 실측 진단,
# docs/logs/cd/deploy/003-oom-crash.md Attempt 5). 진단 끝나면 JRE로 되돌릴 것.
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
# 2026-08-14 진짜 원인 확정 — 반복되는 프로덕션 OOM 크래시(2026-08-12부터, docs/logs/cd/deploy/003-oom-crash.md)의
# 진짜 원인은 컨테이너 메모리가 부족해서가 아니라, **JVM의 컨테이너 메모리 자동 감지(UseContainerSupport)가
# Railway 환경에서 고장나 있었던 것**이다. `railway ssh`로 실행 중인 컨테이너에 직접 들어가 실측 확인함:
# `/sys/fs/cgroup/memory.max`(실제 컨테이너 한도) = 999997740 bytes(≈954MB)인데, 그 상태에서 뜬 JVM이
# `-XX:MaxRAMPercentage=60.0`으로 계산한 MaxHeapSize는 **32178700288 bytes(≈30GB)** — 실제 한도의 30배가
# 넘는 값이었다. 즉 어제 밤 이후 `MaxRAMPercentage`를 70→60으로 조정한 것도 전혀 의미가 없었다(둘 다
# "30GB의 몇 %"라 사실상 무제한). JVM이 스스로는 메모리가 남아돈다고 착각하니 `OutOfMemoryError`를 한 번도
# 자체 감지하지 못했고, 실제 954MB 한도에 부딪히는 순간 리눅스가 예고 없이 컨테이너를 죽였다(그래서 크래시
# 직전 로그에 자바 레벨 에러가 단 한 줄도 안 남았던 것 — 어젯밤 여러 차례 확인한 정황과 정확히 일치).
#
# 대응: 자동 감지(퍼센트 기반)를 신뢰하지 않고 **힙 크기를 고정값으로 직접 지정**한다 — 실제 한도(954MB)
# 안에서 힙 512MB + 메타스페이스 192MB + 나머지(스레드 스택 -Xss512k·다이렉트 버퍼·네이티브)를 위한 여유
# ~250MB로 배분(실측 근거: 위 cgroup 확인 값 기준, 안전 마진 포함).
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=192m", "-Xss512k", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
