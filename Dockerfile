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
# 명시적 상한을 줘서 컨테이너 한도 안에서 예측 가능하게 동작하도록 한다. 힙은 컨테이너 메모리의 70%까지만
# (나머지는 스레드 스택·다이렉트 버퍼·네이티브 라이브러리용 여유), 메타스페이스는 192m로 상한 — 둘 다
# 실측 근거 없는 초기값이라 재발 시 Railway Metrics로 실제 사용량 보고 조정 필요.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:MaxMetaspaceSize=192m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
