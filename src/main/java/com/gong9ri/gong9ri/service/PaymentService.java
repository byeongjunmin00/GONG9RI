package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.client.PortOneClient;
import com.gong9ri.gong9ri.client.PortOnePaymentDetail;
import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
import com.gong9ri.gong9ri.dto.PaymentResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.PaymentStatus;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 생성/확정 (docs/dev/payment/portone/design.md). {@code create}는 이제 "결제 완료"가 아니라
 * "결제 요청 접수"다 — 실제 확정은 {@code confirm}(클라이언트가 PortOne 결제창 완료 후 호출) 또는
 * 웹훅({@code confirmByPgPaymentId})이 PortOne API 재조회 결과를 확인한 뒤에만 이루어진다. 클라이언트가
 * 보내는 "성공했다"는 신호를 그대로 믿지 않는 것이 이 설계의 핵심 원칙이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final PriceTierRepository priceTierRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;
    private final NotificationPublisher notificationPublisher;
    private final PortOneClient portOneClient;

    @Value("${portone.store-id}")
    private String portoneStoreId;

    @Value("${portone.channel-key}")
    private String portoneChannelKey;

    @Transactional
    public PaymentResponse create(MemberUserDetails principal, PaymentCreateRequest request) {
        requireBuyer(principal);
        Member member = principal.getMember();

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isNotYetOpen()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_YET_OPEN);
        }

        GroupBuyTeam team = null;
        Integer amount = product.getBasePrice();
        if (request.teamId() != null) {
            team = groupBuyTeamRepository.findById(request.teamId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
            requireRoomOrAlreadyJoined(team, member);
            amount = resolveTeamPrice(product, team);
        }

        String pgPaymentId = "pay_" + UUID.randomUUID();
        Payment saved = paymentRepository.save(new Payment(member, product, team, amount, pgPaymentId));
        log.info("결제 요청 접수: paymentId={}, pgPaymentId={}, memberId={}, productId={}, teamId={}, amount={}",
                saved.getId(), pgPaymentId, member.getId(), product.getId(), request.teamId(), amount);

        // 판매자 수익 요약(seller_revenue_summary) 증가는 여기서 하지 않는다 — 아직 "요청 접수"일 뿐
        // 실제 승인이 확인되지 않았다(confirm/웹훅에서 서버가 PortOne 재조회로 확인한 뒤에만 반영).
        return PaymentResponse.from(saved, portoneStoreId, portoneChannelKey);
    }

    public PaymentResponse detail(MemberUserDetails principal, Long paymentId) {
        Payment payment = paymentRepository.findByIdWithDetails(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        requireOwner(principal, payment);
        return PaymentResponse.from(payment);
    }

    /**
     * 클라이언트가 PortOne 결제창 완료 후 호출한다 — 클라이언트의 "성공" 응답을 그대로 믿지 않고,
     * 서버가 직접 PortOne API({@code GET /payments/{paymentId}})로 재조회해서 실제 승인 상태·금액이
     * 우리가 기록해둔 것과 일치할 때만 확정한다.
     */
    @Transactional
    public PaymentResponse confirm(MemberUserDetails principal, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        requireOwner(principal, payment);

        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentResponse.from(payment); // 이미 확정됨 — 멱등(중복 confirm 호출 방어).
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }

        PortOnePaymentDetail detail = fetchPaymentDetailOrThrow(payment);
        if (!applyVerificationResult(payment, detail)) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        return PaymentResponse.from(payment);
    }

    /**
     * 웹훅(Transaction.Paid/Transaction.Failed)에서 호출한다 — 웹훅 페이로드 내용 자체를 신뢰하지
     * 않고, 여기서도 동일하게 PortOne API 재조회 결과로만 판단한다. 클라이언트가 confirm()을 호출하지
     * 못한 경우(예: 결제 직후 브라우저 종료)를 보완하는 안전망 역할도 한다.
     */
    @Transactional
    public void confirmByPgPaymentId(String pgPaymentId) {
        Payment payment = paymentRepository.findByPgPaymentId(pgPaymentId).orElse(null);
        if (payment == null) {
            log.warn("웹훅이 가리키는 결제를 찾을 수 없음: pgPaymentId={}", pgPaymentId);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("웹훅 재검증 스킵(이미 확정/실패 처리됨): pgPaymentId={}, status={}", pgPaymentId, payment.getStatus());
            return;
        }
        try {
            PortOnePaymentDetail detail = portOneClient.getPayment(pgPaymentId);
            applyVerificationResult(payment, detail);
        } catch (Exception e) {
            log.error("웹훅 기반 결제 재검증 실패(다음 웹훅 재전송 또는 클라이언트 confirm 호출에 맡김): "
                    + "pgPaymentId={}, error={}", pgPaymentId, e.getMessage(), e);
        }
    }

    private PortOnePaymentDetail fetchPaymentDetailOrThrow(Payment payment) {
        try {
            return portOneClient.getPayment(payment.getPgPaymentId());
        } catch (Exception e) {
            log.error("포트원 결제 조회 실패: paymentId={}, pgPaymentId={}, error={}",
                    payment.getId(), payment.getPgPaymentId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    // 서버가 PortOne에 재조회한 결과로만 확정 여부를 판정한다(클라이언트/웹훅이 보낸 값은 신뢰하지 않음).
    // 금액이 일치하지 않으면 위변조 의심으로 보고 확정하지 않는다(PENDING 유지, 재시도 여지를 남긴다).
    private boolean applyVerificationResult(Payment payment, PortOnePaymentDetail detail) {
        boolean statusOk = "PAID".equals(detail.status());
        boolean amountOk = payment.getAmount().equals(detail.totalAmount());

        if (statusOk && amountOk) {
            payment.confirm();
            sellerRevenueSummaryRepository.incrementPaid(payment.getProduct().getSeller().getId(), payment.getAmount());
            log.info("결제 확정 완료: paymentId={}, pgPaymentId={}, amount={}",
                    payment.getId(), payment.getPgPaymentId(), payment.getAmount());
            // 판매자 "결제 발생" 알림은 여기서만 낸다 — 확정 경로가 클라이언트 confirm()과 웹훅
            // 두 갈래지만 둘 다 이 메서드로 모이고, 양쪽 호출부가 모두 PENDING일 때만 여기 도달하도록
            // 막고 있어서 같은 결제로 알림이 두 번 생기지 않는다(중복 알림 방지).
            notificationPublisher.paymentReceived(payment.getProduct().getSeller().getId(),
                    payment.getMember().getId(), payment.getProduct().getName(), payment.getAmount());
            return true;
        }

        if ("FAILED".equals(detail.status())) {
            payment.fail();
        }
        log.warn("결제 확정 보류(서버 재검증 불일치): paymentId={}, pgPaymentId={}, pgStatus={}, "
                        + "expectedAmount={}, actualAmount={}",
                payment.getId(), payment.getPgPaymentId(), detail.status(), payment.getAmount(), detail.totalAmount());
        return false;
    }

    // 팀 참가는 team/join·team/create에서 이미 완결된다 — 결제는 그 결과를 다시 검증만 한다.
    // 아직 참가하지 않은 멤버가 이미 정원이 찬 팀으로 결제를 시도하는 경합만 방어적으로 막는다.
    private void requireRoomOrAlreadyJoined(GroupBuyTeam team, Member member) {
        boolean alreadyJoined = teamParticipationRepository.existsByTeamIdAndMemberId(team.getId(), member.getId());
        if (!alreadyJoined && team.getCurrentCount() >= team.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.TEAM_FULL);
        }
    }

    // 팀의 목표 인원(정원, 팀 생성 시점에 고정된 스냅샷)을 기준으로 가격 구간을 조회한다.
    // 결제 시점의 실시간 참여 인원(currentCount)을 쓰지 않는 이유: 같은 팀 안에서 먼저 결제한 사람과
    // 나중에 결제한 사람의 단가가 달라지는 형평성 문제를 없애기 위함 — 팀 전체가 항상 동일한 금액을 낸다.
    private Integer resolveTeamPrice(Product product, GroupBuyTeam team) {
        List<PriceTier> tiers = priceTierRepository.findByProductIdOrderByMinCountAsc(product.getId());
        Integer price = product.getBasePrice();
        for (PriceTier tier : tiers) {
            if (team.getMaxParticipants() >= tier.getMinCount()) {
                price = tier.getPrice();
            } else {
                break;
            }
        }
        return price;
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireOwner(MemberUserDetails principal, Payment payment) {
        if (!payment.isOwnedBy(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
