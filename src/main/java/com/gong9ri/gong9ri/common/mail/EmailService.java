package com.gong9ri.gong9ri.common.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 로그인 고도화 2단계 — 이메일 인증/비밀번호 재설정 메일 발송. 이메일이 2종뿐이라 별도 템플릿
 * 엔진(Thymeleaf 등) 없이 인라인 문자열로 충분하다고 판단했다.
 *
 * <p>{@code @Async}: 기존 {@code AsyncConfig}의 기본 executor를 그대로 재사용한다(qualifier 없음,
 * 새 스레드풀 안 만듦). 메일 발송 자체가 느리거나 실패해도 호출자(회원가입 트랜잭션, 재설정 요청
 * 응답)를 막으면 안 된다는 원칙 — AI 기능들의 장애격리 원칙과 같은 판단.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/api/auth/verify-email?token=" + token;
        String body = "GONG9RI 회원가입을 완료하려면 아래 링크를 클릭해서 이메일을 인증해주세요.\n\n"
                + link
                + "\n\n이 링크는 24시간 동안만 유효합니다.";
        send(to, "[GONG9RI] 이메일 인증을 완료해주세요", body);
    }

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        String link = baseUrl + "/reset-password.html?token=" + token;
        String body = "비밀번호를 재설정하려면 아래 링크를 클릭해주세요.\n\n"
                + link
                + "\n\n이 링크는 30분 동안만 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해도 됩니다.";
        send(to, "[GONG9RI] 비밀번호 재설정", body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // 메일 발송 실패가 회원가입/재설정 요청 자체를 실패시키면 안 된다(장애격리).
            log.warn("이메일 발송 실패: to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }
}
