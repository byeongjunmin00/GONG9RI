package com.gong9ri.gong9ri.common.mail;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SendGrid REST API(v3, {@code POST /mail/send})로 실제 발송한다. 별도 SDK 의존성 없이
 * {@code RestClient}(spring-web 내장)로 직접 호출한다 — {@code PortOneApiClient}와 같은 판단(SDK
 * 의존성 없이 REST 호출로 충분).
 *
 * <p>발신 주소({@code sendgrid.from-email})는 SendGrid 콘솔에서 "Single Sender Verification"으로
 * 인증한 주소여야 한다 — 이 프로젝트는 커스텀 도메인이 없어(Railway 서브도메인만 사용) 도메인 전체
 * 인증(Domain Authentication) 대신 이메일 주소 하나만 인증하는 이 방식을 쓴다.
 */
@Component
public class SendGridEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;

    public SendGridEmailSender(
            @Value("${sendgrid.api-key}") String apiKey,
            @Value("${sendgrid.from-email}") String fromEmail) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.restClient = RestClient.builder().baseUrl("https://api.sendgrid.com/v3").build();
    }

    @Override
    public void send(String to, String subject, String body) {
        restClient.post()
                .uri("/mail/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SendRequest(
                        List.of(new Personalization(List.of(new EmailAddress(to)))),
                        new EmailAddress(fromEmail),
                        subject,
                        List.of(new Content("text/plain", body))))
                .retrieve()
                .toBodilessEntity();
    }

    private record SendRequest(
            List<Personalization> personalizations, EmailAddress from, String subject, List<Content> content) {
    }

    private record Personalization(List<EmailAddress> to) {
    }

    private record EmailAddress(String email) {
    }

    private record Content(String type, String value) {
    }
}
