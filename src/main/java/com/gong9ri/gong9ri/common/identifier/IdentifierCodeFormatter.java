package com.gong9ri.gong9ri.common.identifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 회원번호 · 상품코드 · 주문번호 · 공구팀 번호의 포맷 규칙(SSOT는 {@code docs/policy/identifier-code.md}).
 *
 * <p>전부 <b>PK 파생</b>이다 — 별도 채번 카운터 테이블 없이, 이미 유일·순차적인 auto-increment PK를
 * 사람이 읽는 문자열로 변환만 한다(카운터 증가의 동시성 문제 자체가 없다, PK 채번은 DB가 이미 원자적으로
 * 보장). 호출부(각 서비스의 create 메서드)는 엔티티를 저장해 PK가 확정된 직후에만 이 포맷터를 호출해야
 * 한다 — PK가 없으면(저장 전) 코드를 만들 수 없다.
 */
public final class IdentifierCodeFormatter {

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private IdentifierCodeFormatter() {
    }

    /** 회원번호: {@code M} + PK 7자리 zero-pad. 예: {@code M0000001} */
    public static String memberCode(Long id) {
        return "M" + zeroPad(id, 7);
    }

    /** 상품코드: {@code P} + PK 7자리 zero-pad. 예: {@code P0000001} */
    public static String productCode(Long id) {
        return "P" + zeroPad(id, 7);
    }

    /** 공구팀 번호: {@code T} + PK 7자리 zero-pad. 예: {@code T0000001} */
    public static String teamNo(Long id) {
        return "T" + zeroPad(id, 7);
    }

    /**
     * 주문번호: {@code O} + 결제 접수일({@code paidAt}, {@code yyyyMMdd}) + {@code -} + PK 6자리 zero-pad.
     * 예: {@code O20260822-000001}. 회원/상품/공구팀과 달리 날짜 접두어가 들어간다 — 정산 대사·일자별
     * CS 조회 편의(사용자 확정, {@code docs/dev/ongoing/admin-identifier-codes.md} "확정 2").
     */
    public static String orderNo(Long id, LocalDateTime paidAt) {
        return "O" + paidAt.format(ORDER_DATE_FORMAT) + "-" + zeroPad(id, 6);
    }

    private static String zeroPad(Long id, int width) {
        return String.format("%0" + width + "d", id);
    }
}
