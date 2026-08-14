package com.gong9ri.gong9ri.common.mail;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    // 발신 표시 이름 — 실제 발신 주소(sendgrid.from-email)는 Single Sender Verification으로 인증한
    // 개인 이메일 그대로 두되, 받는 사람 메일함에 그 주소 대신 "GONG9RI"로 보이게 한다. 표시 이름은
    // 도메인 소유권 검증이 필요 없는 순수 문자열이라 커스텀 도메인 없이도 바로 적용 가능하다.
    private static final String FROM_DISPLAY_NAME = "GONG9RI";

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
                        List.of(new Personalization(List.of(new EmailAddress(to, null)))),
                        new EmailAddress(fromEmail, FROM_DISPLAY_NAME),
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

    // name은 from에서만 쓰고(표시 이름), to에서는 null로 둔다 — null이면 JSON 직렬화에서 필드 자체를
    // 생략해야 SendGrid API가 정상 처리한다("name": null을 그대로 보내면 형식 오류로 거부될 수 있음).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record EmailAddress(String email, String name) {
    }

    private record Content(String type, String value) {
    }
}
