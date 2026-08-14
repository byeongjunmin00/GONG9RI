package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.RefundRequestCreateRequest;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.dto.RefundRequestRejectRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.event.RefundRequestApprovedEvent;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 환불 요청/승인/거절 (docs/dev/ongoing/team-leave-and-refund-request.md).
 *
 * <p><b>매우 중요한 제약</b>: 팀이 딸린 결제({@code payment.team != null})의 환불은 오직 참여 취소
 * ({@code TeamService.leave} → {@link #createFromTeamLeave})로만 일어난다. 이 클래스의 구매자 직접
 * 요청({@link #createDirect})은 솔로 구매 건에만 허용되고, 팀 결제로 시도하면
 * {@code TEAM_PAYMENT_REFUND_NOT_ALLOWED}로 거절한다 — 2인 목표 팀에 계정 2개로 결제 후 하나만
 * 환불하면 실질적 1인 결제인데 2인 구간 할인가로 사는 악용을 막기 위함(사용자 확인).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundRequestService {

    private static final String SELLER_APPROVED_REASON = "판매자 승인에 따른 환불";
    private static final String AUTO_REFUND_ON_LEAVE_REASON = "참여 취소(상품별 자동환불 설정)에 따른 환불";

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 솔로 구매(payment.team == null) 건에 대한 구매자 직접 환불 요청 — 사유 입력 필수, 항상 판매자
    // 승인/거절 절차를 거친다(자동 환불 설정과 무관, 이미 배송됐을 수 있어서).
    @Transactional
    public RefundRequestResponse createDirect(MemberUserDetails principal, Long paymentId,
            RefundRequestCreateRequest request) {
        requireBuyer(principal);
        Member member = principal.getMember();

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        requireOwner(member, payment);

        if (payment.getTeam() != null) {
            throw new BusinessException(ErrorCode.TEAM_PAYMENT_REFUND_NOT_ALLOWED);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }
        if (refundRequestRepository.existsByPayment_IdAndStatus(paymentId, RefundRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
        }

        RefundRequest saved = refundRequestRepository.save(new RefundRequest(payment, member, request.reason()));
        log.info("솔로 구매 직접 환불 요청 생성: refundRequestId={}, paymentId={}, memberId={}",
                saved.getId(), paymentId, member.getId());
        return RefundRequestResponse.from(saved);
    }

    /**
     * 참여 취소({@code TeamService.leave})가 호출한다 — 이미 열려 있는 그 트랜잭션(팀 row 비관적 락 보유)
     * 에 그대로 참여한다. 팀 결제 전용, 사유 없음("참여 취소"가 곧 사유). 상품별 자동환불 설정이 켜져
     * 있으면 즉시 APPROVED로 만들고, 그 실제 PortOne 취소 호출은 AFTER_COMMIT 이벤트로 미룬다(락이
     * 풀린 뒤에만 외부 HTTP를 호출해야 하므로 — TeamService.leave가 아직 락을 쥔 채로 이 메서드를
     * 호출하고 있다는 점이 핵심 전제).
     */
    @Transactional
    public void createFromTeamLeave(Payment payment, Member requester, boolean autoRefundOnCancel) {
        RefundRequest saved = refundRequestRepository.save(new RefundRequest(payment, requester, null));

        if (!autoRefundOnCancel) {
            log.info("참여 취소 환불 요청 생성(대기): refundRequestId={}, paymentId={}, memberId={}",
                    saved.getId(), payment.getId(), requester.getId());
            return;
        }

        saved.approve();
        eventPublisher.publishEvent(
                new RefundRequestApprovedEvent(saved.getId(), payment.getId(), AUTO_REFUND_ON_LEAVE_REASON));
        log.info("참여 취소 환불 요청 자동 승인(상품별 자동환불 설정): refundRequestId={}, paymentId={}, memberId={}",
                saved.getId(), payment.getId(), requester.getId());
    }

    // 판매자 수동 승인 — 실제 PortOne 취소 호출은 AFTER_COMMIT 이벤트로 미룬다.
    @Transactional
    public RefundRequestResponse approve(MemberUserDetails principal, Long refundRequestId) {
        requireSeller(principal);
        RefundRequest refundRequest = findWithOwnerCheck(principal, refundRequestId);

        if (refundRequest.getStatus() != RefundRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_DECIDED);
        }

        refundRequest.approve();
        eventPublisher.publishEvent(new RefundRequestApprovedEvent(
                refundRequest.getId(), refundRequest.getPayment().getId(), SELLER_APPROVED_REASON));
        log.info("환불 요청 승인: refundRequestId={}, paymentId={}, sellerId={}",
                refundRequestId, refundRequest.getPayment().getId(), principal.getMember().getId());
        return RefundRequestResponse.from(refundRequest);
    }

    // 판매자 거절 — 자유 텍스트가 아니라 사유 템플릿을 남긴다. 결제는 PAID로 그대로 유지된다.
    @Transactional
    public RefundRequestResponse reject(MemberUserDetails principal, Long refundRequestId,
            RefundRequestRejectRequest request) {
        requireSeller(principal);
        RefundRequest refundRequest = findWithOwnerCheck(principal, refundRequestId);

        if (refundRequest.getStatus() != RefundRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_DECIDED);
        }

        refundRequest.reject(request.rejectionReason());
        log.info("환불 요청 거절: refundRequestId={}, paymentId={}, sellerId={}, rejectionReason={}",
                refundRequestId, refundRequest.getPayment().getId(), principal.getMember().getId(),
                request.rejectionReason());
        return RefundRequestResponse.from(refundRequest);
    }

    private RefundRequest findWithOwnerCheck(MemberUserDetails principal, Long refundRequestId) {
        RefundRequest refundRequest = refundRequestRepository.findByIdWithPaymentAndProduct(refundRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
        if (!refundRequest.getPayment().getProduct().getSeller().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return refundRequest;
    }

    private void requireOwner(Member member, Payment payment) {
        if (!payment.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireSeller(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
