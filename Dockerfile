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
ENTRYPOINT ["java", "-jar", "app.jar"]
