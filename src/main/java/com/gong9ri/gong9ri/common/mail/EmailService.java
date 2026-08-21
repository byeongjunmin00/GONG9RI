package com.gong9ri.gong9ri.common.mail;

import com.gong9ri.gong9ri.common.config.AppUrlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 로그인 고도화 2단계 — 이메일 인증/비밀번호 재설정 메일 발송. 이메일이 2종뿐이라 별도 템플릿
 * 엔진(Thymeleaf 등) 없이 인라인 문자열로 충분하다고 판단했다.
 *
 * <p>{@code @Async}: 기존 {@code AsyncConfig}의 기본 executor를 그대로 재사용한다(qualifier 없음,
 * 새 스레드풀 안 만듦). 메일 발송 자체가 느리거나 실패해도 호출자(회원가입 트랜잭션, 재설정 요청
 * 응답)를 막으면 안 된다는 원칙 — AI 기능들의 장애격리 원칙과 같은 판단.
 *
 * <p>실제 발송 트랜스포트는 {@link EmailSender}로 분리돼 있다(처음엔 {@code JavaMailSender}로 Gmail
 * SMTP 직접 연결을 썼으나, Railway가 아웃바운드 SMTP를 막고 있어 프로덕션에서 발송이 전혀 안 되는 걸
 * 실측으로 확인 후 HTTP 기반 {@code SendGridEmailSender}로 교체했다 — {@code
 * docs/logs/cd/deploy/004-smtp-blocked.md}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private final EmailSender emailSender;
    private final AppUrlProperties appUrl;

    @Async
    public void sendVerificationEmail(String to, String token) {
        String link = appUrl.url("/api/auth/verify-email?token=" + token);
        String body = "GONG9RI 회원가입을 완료하려면 아래 링크를 클릭해서 이메일을 인증해주세요.\n\n"
                + link
                + "\n\n이 링크는 24시간 동안만 유효합니다.";
        send(to, "[GONG9RI] 이메일 인증을 완료해주세요", body);
    }

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        String link = appUrl.url("/reset-password.html?token=" + token);
        String body = "비밀번호를 재설정하려면 아래 링크를 클릭해주세요.\n\n"
                + link
                + "\n\n이 링크는 30분 동안만 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해도 됩니다.";
        send(to, "[GONG9RI] 비밀번호 재설정", body);
    }

    private void send(String to, String subject, String body) {
        try {
            emailSender.send(to, subject, body);
        } catch (Exception e) {
            // 메일 발송 실패가 회원가입/재설정 요청 자체를 실패시키면 안 된다(장애격리).
            log.warn("이메일 발송 실패: to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }
}
