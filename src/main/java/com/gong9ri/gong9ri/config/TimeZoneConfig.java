package com.gong9ri.gong9ri.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 기본 시간대를 한국(Asia/Seoul)으로 고정한다.
 *
 * <p><b>왜 필요한가</b> — 이 프로젝트는 시간을 전부 {@code LocalDateTime}(시간대 정보 없음)으로 다룬다.
 * 그러면 "이 값이 어느 시간대인지"는 <b>JVM 기본 시간대</b>가 결정하는데, 배포 환경(Railway 컨테이너)은
 * 기본이 UTC라 한국 사용자가 입력·조회하는 시각과 9시간 어긋났다(2026-08-20 사용자 리포트:
 * "날짜는 맞는데 시간이 틀리다").
 *
 * <p>표시만 어긋난 게 아니라 <b>판정 로직도 틀렸다</b>. 판매자가 상품 공개 시각(`openAt`)을 한국 시간으로
 * 입력하는데 {@code Product.isNotYetOpen()}은 UTC 기준 {@code LocalDateTime.now()}와 비교해서,
 * "9월 1일 0시 공개"로 설정한 상품이 실제로는 오전 9시에 열렸다.
 *
 * <p><b>왜 이 방식인가</b> — Dockerfile의 JVM 옵션이나 Railway 환경변수로 설정하면 배포 환경에만 적용돼
 * 로컬 개발·CI(UTC 러너)와 동작이 달라진다. 애플리케이션 코드에서 고정하면 <b>어디서 실행하든 동일</b>하고,
 * {@code TimeZoneConfigTest}로 회귀도 막을 수 있다.
 *
 * <p>DB 컬럼이 전부 {@code DATETIME}(시간대 없음)이고 {@code TIMESTAMP}는 하나도 없어서, MySQL 드라이버가
 * 값을 변환하지 않는다 — 즉 이 설정은 {@code LocalDateTime.now()}가 만드는 값에만 영향을 준다.
 */
@Slf4j
@Configuration
public class TimeZoneConfig {

    private static final String APPLICATION_TIME_ZONE = "Asia/Seoul";

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_TIME_ZONE));
        log.info("애플리케이션 기본 시간대 설정 완료: {}", APPLICATION_TIME_ZONE);
    }
}
