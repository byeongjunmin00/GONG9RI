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
# Railway 컨테이너 메모리 한도(1GB)를 JVM에 명시적으로 안 알려주고 있었다(옵션 전혀 없음) — 반복되는
# 프로덕션 OOM 크래시(2026-08-12, docs/logs/cd/deploy/003-oom-crash.md)의 유력 원인. 힙/메타스페이스에
# 명시적 상한을 줘서 컨테이너 한도 안에서 예측 가능하게 동작하도록 한다. 힙은 컨테이너 메모리의 60%까지만
# (나머지는 스레드 스택·다이렉트 버퍼·네이티브 라이브러리용 여유), 메타스페이스는 192m로 상한.
#
# 2026-08-13 재발 대응(Railway Metrics 실측: 조용할 때도 baseline이 이미 997MB/1000MB, 요청 좀 들어오면
# 1.7~1.8GB까지 튐) — 최초 70%는 실측 근거 없는 초기값이었고, 그날 밤 JVM 옵션이 하나도 없을 때조차
# baseline이 825MB였다는 기록(힙 기본값 25%=약 250MB뿐이던 시점)을 다시 보면 애초에 non-heap 쪽이 더
# 컸다는 뜻이라 힙만 줄이는 건 근본 대응이 아니었다. 톰캣 기본 스레드풀(200개, 스레드당 기본 스택 ~1MB
# → 최악의 경우 200MB)이 non-heap의 유력 용의자라 -Xss로 스레드당 스택을 줄이고
# (application.yaml의 server.tomcat.threads.max=50과 함께 적용), MaxRAMPercentage는 60%로 소폭만
# 낮춰 힙을 과하게 굶기지 않으면서 non-heap 여유를 늘리는 쪽으로 조정(팀원 제안).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-XX:MaxMetaspaceSize=192m", "-Xss512k", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
