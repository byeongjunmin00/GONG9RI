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
 * {@code MemberSignedUpEvent} 구독자 — 회원가입 트랜잭션이 실제로 커밋된 이후에만 인증 메일을
 * 보내야 한다({@code TeamRefundedEvent}/{@code TeamCapacityChangedEvent}와 동일한 이유로
 * {@code AFTER_COMMIT}을 쓴다) — 롤백된 가입에 메일을 보내면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSignedUpEventListener {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final TokenService tokenService;
    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberSignedUp(MemberSignedUpEvent event) {
        String token = tokenService.issue("email-verify", event.memberId(), VERIFICATION_TOKEN_TTL);
        emailService.sendVerificationEmail(event.email(), token);
    }
}
