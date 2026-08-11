package com.gong9ri.gong9ri.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 로컬 `.env` 파일(레포 루트, gitignore됨)을 스프링 {@code Environment}에 property source로 얹는다.
 * 처음엔 {@code spring-dotenv} 라이브러리(META-INF/spring.factories 기반
 * {@code SpringApplicationRunListener})를 썼는데, 이 Spring Boot 4.1.0 조합에서 실제로 로딩이 안 되는
 * 걸 발견했다(직접 {@code export}한 OS 환경변수는 정상 작동, 라이브러리 경유만 실패 — 실제로 재현·확인함,
 * docs/dev/ai/product-suggestion/design.md). 라이브러리의 최신 Boot 버전 호환성에 기대는 대신, 몇 줄짜리
 * 이 클래스로 직접 대체했다.
 *
 * <p>`.env`가 없으면 조용히 무시된다 — 배포 환경(Railway)엔 이 파일이 없고 실제 환경변수를 플랫폼이
 * 직접 주입하므로 이 클래스는 그 경로에서 아무 일도 하지 않는다. {@link EnvironmentPostProcessor}는
 * 로깅 인프라가 뜨기 전에 실행되는 아주 이른 단계라 SLF4J 로거를 쓰지 않는다(표준 관례).
 *
 * <p><b>등록은 {@code META-INF/spring.factories}로 한다</b>({@code org.springframework.boot.EnvironmentPostProcessor=...}),
 * {@code META-INF/spring/*.imports} 방식이 아니다 — Boot 4.1.0 jar를 직접 까봐도 이 SPI는 여전히
 * spring.factories로 등록돼 있다({@code AutoConfiguration.imports} 같은 다른 SPI와 헷갈리기 쉬움).
 * 처음에 `.imports` 파일로 등록했다가 {@code postProcessEnvironment}가 아예 호출되지 않는 걸로
 * 한참 헤맸다 — 이 클래스가 조용히 무시된다면 등록 파일부터 의심할 것.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = Path.of(".env").toAbsolutePath();
        if (!Files.exists(envFile)) {
            return;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex < 0) {
                    continue;
                }
                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                properties.put(key, value);
            }
        } catch (IOException e) {
            return;
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("dotenv", properties));
        }
    }
}
