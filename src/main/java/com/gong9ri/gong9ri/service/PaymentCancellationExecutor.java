package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.PortOneCancelResult;
import com.gong9ri.gong9ri.client.PortOneClient;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * "PortOne 결제취소 API를 호출하고 그 결과를 DB에 반영한다"는 한 결제 단위의 취소 실행 로직을 한 곳에
 * 모아둔다 — 원래 {@code TeamPaymentsRefundRequestedEventListener}(공구팀 미성사 자동환불) 안에만
 * 있던 코드였는데, 판매자 승인 환불({@code RefundRequestApprovedEventListener})도 정확히 같은 절차
 * (취소 대상 조회 → PortOne 호출 → 결과 반영, 실패해도 예외를 삼키고 로그만 남김)를 타야 해서
 * 추출했다(docs/dev/ongoing/team-leave-and-refund-request.md — "기존 PaymentRefundService의 취소
 * 실행 로직을 그대로 재사용, PortOne 취소 API를 새로 호출하는 코드를 중복 작성하지 마라").
 *
 * <p>{@code PaymentRefundService}가 직접 이 오케스트레이션을 하지 않는 이유는 그대로다 — {@code
 * findCancelTarget}/{@code applyCancelResult}는 서로 다른 트랜잭션 경계를 가진 별도 메서드라, 같은
 * 클래스 안에서 self-invocation으로 호출하면 프록시를 안 타서 트랜잭션 경계가 깨진다. 이 클래스는
 * 별도 빈이라 정상적으로 각 메서드의 트랜잭션 프록시를 그대로 거친다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancellationExecutor {

    private final PaymentRefundService paymentRefundService;
    private final PortOneClient portOneClient;

    public void cancelOne(Long paymentId, String reason) {
        Optional<PaymentRefundService.CancelTarget> target = paymentRefundService.findCancelTarget(paymentId);
        if (target.isEmpty()) {
            log.warn("환불취소 대상 아님(이미 처리됐거나 존재하지 않음): paymentId={}", paymentId);
            return;
        }

        try {
            PortOneCancelResult result = portOneClient.cancelPayment(target.get().pgPaymentId(), reason);
            paymentRefundService.applyCancelResult(paymentId, result);
        } catch (Exception e) {
            log.error("포트원 결제취소 API 호출 실패: paymentId={}, pgPaymentId={}, error={}",
                    paymentId, target.get().pgPaymentId(), e.getMessage(), e);
        }
    }
}
