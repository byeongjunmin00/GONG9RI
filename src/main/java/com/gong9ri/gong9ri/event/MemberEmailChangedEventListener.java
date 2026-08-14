package com.gong9ri.gong9ri.event;

import com.gong9ri.gong9ri.common.mail.EmailService;
import com.gong9ri.gong9ri.common.security.TokenService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code MemberEmailChangedEvent} 구독자 — {@code MemberSignedUpEventListener}와 동일한 이유로
 * 정보수정 트랜잭션이 실제로 커밋된 이후에만({@code AFTER_COMMIT}) 새 이메일로 인증 메일을 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEmailChangedEventListener {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final TokenService tokenService;
    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberEmailChanged(MemberEmailChangedEvent event) {
        String token = tokenService.issue("email-verify", event.memberId(), VERIFICATION_TOKEN_TTL);
        emailService.sendVerificationEmail(event.email(), token);
    }
}
