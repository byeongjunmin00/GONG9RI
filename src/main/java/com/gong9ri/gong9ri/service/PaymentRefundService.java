package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.event.TeamRefundedEvent;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구팀 미성사 자동환불의 실제 PortOne 결제취소 반영 — "포트원 결제취소 API 호출 → 성공 확인 후 DB
 * 상태 전환"(docs/dev/ongoing/payment-portone.md) 원칙을 지킨다. 실제 PortOne API 호출은 이 서비스가
 * 하지 않는다({@code TeamPaymentsRefundRequestedEventListener}가 호출) — 이 서비스는 "취소 대상
 * 조회"(짧은 읽기)와 "취소 결과를 DB에 반영"(쓰기 트랜잭션)만 맡아, 외부 HTTP 호출이 트랜잭션(DB 커넥션)을
 * 붙잡지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public record CancelTarget(Long paymentId, String pgPaymentId) {
    }

    // PortOne 취소 API 호출 앞에서 짧게 조회만 한다 — 트랜잭션을 오래 열어두고 외부 HTTP를 기다리지 않는다.
    public Optional<CancelTarget> findCancelTarget(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .map(payment -> new CancelTarget(payment.getId(), payment.getPgPaymentId()));
    }

    // PortOne 결제취소 API 응답을 확인한 뒤에만 호출된다(호출부: TeamPaymentsRefundRequestedEventListener).
    @Transactional
    public void applyCancelResult(Long paymentId, PortOneCancelResult result) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PAID) {
            log.info("결제취소 반영 스킵(이미 처리됐거나 존재하지 않음): paymentId={}", paymentId);
            return;
        }

        if (PortOneCancelResult.SUCCEEDED.equals(result.status())) {
            confirmRefunded(payment);
        } else if (PortOneCancelResult.REQUESTED.equals(result.status())) {
            // 비동기 처리 중 — 웹훅(Transaction.Cancelled)이 최종 확정할 때까지 중간 상태로 대기한다.
            payment.markRefundPending();
            log.info("포트원 결제취소 비동기 처리 중(REQUESTED), 웹훅 최종 확정 대기: paymentId={}, pgPaymentId={}",
                    paymentId, payment.getPgPaymentId());
        } else {
            log.error("포트원 결제취소 실패 응답, 결제는 PAID 상태로 유지(수동 확인 필요): paymentId={}, pgPaymentId={}, status={}",
                    paymentId, payment.getPgPaymentId(), result.status());
        }
    }

    // 웹훅 Transaction.Cancelled 전용 — REQUESTED로 대기 중이던 취소가 최종 확정됐을 때 호출된다.
    // 상태 기반 가드가 곧 멱등성 보장이라, 같은 웹훅이 재전송돼도(PortOne 최대 5회 재시도) 안전하다.
    @Transactional
    public void confirmRefundedByPgPaymentId(String pgPaymentId) {
        Payment payment = paymentRepository.findByPgPaymentId(pgPaymentId).orElse(null);
        if (payment == null) {
            log.warn("웹훅이 가리키는 결제를 찾을 수 없음: pgPaymentId={}", pgPaymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("이미 환불 확정된 결제, 웹훅 중복 처리 스킵: pgPaymentId={}", pgPaymentId);
            return;
        }
        confirmRefunded(payment);
    }

    // sellerId는 반드시 sellerRevenueSummaryRepository.applyRefund 호출(flush+clear) 이전에 확보해야
    // 한다 — clear 이후에는 아직 초기화 안 된 지연 연관관계 탐색(product→seller)이 detached 상태에서
    // 실패할 수 있다(team/deadline-check의 기존 patterns과 동일한 주의사항, PaymentRepositoryImpl 등 참고).
    private void confirmRefunded(Payment payment) {
        payment.refund();
        Long sellerId = payment.getProduct().getSeller().getId();
        int rowsAffected = sellerRevenueSummaryRepository.applyRefund(sellerId, payment.getAmount(), 1L);
        if (rowsAffected == 0) {
            log.warn("판매자 수익 요약 환불 반영 실패(요약 행 없음, 백필 필요 추정): sellerId={}, paymentId={}, amount={}",
                    sellerId, payment.getId(), payment.getAmount());
        }

        // 팀 결제인 경우에만 "환불 완료" 알림 이벤트를 낸다(혼자구매는 이 스코프의 환불 트리거 대상이 아님).
        // team/deadline-check가 팀 단위로 한 번에 발행하던 것과 달리, 이제는 결제 건이 실제로 확정될
        // 때마다(비동기 REQUESTED가 섞이면 서로 다른 시점에) 개별 발행한다 — 같은 팀 판매자가 결제 건수만큼
        // 여러 번 알림을 받을 수 있다는 뜻이다(docs/dev/payment/portone/design.md에 알려진 동작으로 기록).
        if (payment.getTeam() != null) {
            eventPublisher.publishEvent(new TeamRefundedEvent(
                    payment.getTeam().getId(), sellerId, List.of(payment.getMember().getId())));
        }

        log.info("결제 환불 확정: paymentId={}, pgPaymentId={}, amount={}",
                payment.getId(), payment.getPgPaymentId(), payment.getAmount());
    }
}
