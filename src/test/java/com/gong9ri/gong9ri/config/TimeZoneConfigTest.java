package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 애플리케이션 기본 시간대가 한국으로 고정돼 있는지 검증한다.
 *
 * <p>이 프로젝트는 시간을 전부 {@code LocalDateTime}(시간대 없음)으로 다뤄서, JVM 기본 시간대가 바뀌면
 * 저장되는 값의 의미 자체가 달라진다. 배포 환경이 UTC라 9시간 어긋났던 실제 버그가 있었고
 * (`docs/dev/ongoing`이 아니라 `changes/`에 기록), 이 테스트는 그 회귀를 막는다.
 *
 * <p>CI 러너는 UTC라 {@code TimeZoneConfig}가 없으면 이 테스트가 실패한다 — 즉 설정이 배포 환경에만
 * 적용되고 코드에는 없는 상태를 잡아낸다.
 */
@SpringBootTest
class TimeZoneConfigTest {

    @Test
    @DisplayName("애플리케이션 기본 시간대는 Asia/Seoul이다")
    void defaultTimeZoneIsSeoul() {
        assertEquals(ZoneId.of("Asia/Seoul"), TimeZone.getDefault().toZoneId(),
                "LocalDateTime 기반이라 기본 시간대가 곧 저장 값의 의미가 된다");
    }
}
