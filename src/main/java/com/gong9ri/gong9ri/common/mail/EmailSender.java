package com.gong9ri.gong9ri.common.mail;

/**
 * 실제 이메일 발송 트랜스포트를 추상화한다. Railway가 아웃바운드 SMTP(587번 포트)를 막고 있어(2026-08-12
 * 실측 확인, {@code docs/logs/cd/deploy/004-smtp-blocked.md}) {@code JavaMailSender}로는 프로덕션에서
 * 발송이 아예 안 됐다 — HTTP(443) 기반의 {@link SendGridEmailSender}로 교체한 이유가 이거다. 테스트에서는
 * 항상 {@code @MockitoBean}으로 대체해 실제 네트워크 호출을 하지 않는다({@code PortOneClient}와 같은 패턴).
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
