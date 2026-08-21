package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.NotificationListResponse;
import com.gong9ri.gong9ri.dto.NotificationResponse;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.dto.SellerOrderResponse;
import com.gong9ri.gong9ri.dto.SellerProductResponse;
import com.gong9ri.gong9ri.dto.SellerTeamResponse;
import com.gong9ri.gong9ri.dto.ShipmentUpdateRequest;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.ShipmentStatus;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerMypageService {

    private final ProductRepository productRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;
    private final NotificationRepository notificationRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final NotificationPublisher notificationPublisher;

    public List<SellerOrderResponse> orders(MemberUserDetails principal) {
        requireSeller(principal);
        return paymentRepository.findAllBySellerIdWithProductAndMemberAndTeam(principal.getMember().getId()).stream()
                .map(SellerOrderResponse::from)
                .toList();
    }

    // 판매자가 자기 상품 주문의 배송 단계/택배사/송장번호를 직접 바꾼다(007). 순서 강제 없이 4단계 중
    // 아무거나로 자유롭게 전환 가능 — 단, 배송중/배송완료는 송장번호가 있어야 하고, 환불됐거나 아직
    // 배송 대상이 아닌 주문(공구 모집중/실패)은 변경 자체가 거절된다.
    @Transactional
    public SellerOrderResponse updateShipment(MemberUserDetails principal, Long paymentId,
            ShipmentUpdateRequest request) {
        requireSeller(principal);
        Payment payment = paymentRepository.findByIdWithDetails(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getProduct().getSeller().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!SellerOrderResponse.isShipmentManageable(payment)) {
            throw new BusinessException(ErrorCode.SHIPMENT_STATUS_NOT_APPLICABLE);
        }

        ShipmentStatus newStatus = request.shipmentStatus();
        boolean trackingRequired = newStatus == ShipmentStatus.IN_TRANSIT || newStatus == ShipmentStatus.DELIVERED;
        if (trackingRequired && (request.trackingNumber() == null || request.trackingNumber().isBlank())) {
            throw new BusinessException(ErrorCode.TRACKING_NUMBER_REQUIRED);
        }

        payment.updateShipment(newStatus, request.trackingCarrier(), request.trackingNumber());
        notificationPublisher.shipmentUpdated(principal.getMember().getId(), payment.getMember().getId(),
                payment.getProduct().getName(), newStatus.label());
        return SellerOrderResponse.from(payment);
    }

    public List<SellerProductResponse> products(MemberUserDetails principal) {
        requireSeller(principal);
        return productRepository.findAllBySellerIdOrderByCreatedAtDesc(principal.getMember().getId()).stream()
                .map(SellerProductResponse::from)
                .toList();
    }

    // 더 이상 캐싱하지 않는다(docs/db/seller_revenue_summary.md, 2026-08-06) — seller_revenue_summary
    // 요약 행을 단순 조회하는 순수 읽기다. 이 값은 결제/환불 트랜잭션(PaymentService.create,
    // TeamDeadlineService.processDeadline) 안에서 즉시 갱신되므로 항상 정확하다.
    // incrementPaid가 upsert라 결제가 한 번이라도 있었다면 요약 행이 반드시 존재한다 — 요약 행이
    // 없다는 건 결제가 아예 없었다는 뜻이므로 그냥 0을 반환한다. 조회 시점에 행을 만드는 쓰기
    // (지연 부트스트랩)는 더 이상 하지 않는다 — 그 방식이 "부트스트랩 vs 신규 결제" 경쟁 상태의
    // 근본 원인이었다(docs/dev/mypage/view/changes/004-upsert-fix.md). 쓰기가 없으므로
    // 클래스 기본 @Transactional(readOnly = true)를 그대로 쓴다(메서드 레벨 오버라이드 없음).
    public RevenueResponse revenue(MemberUserDetails principal) {
        requireSeller(principal);
        Long sellerId = principal.getMember().getId();
        return sellerRevenueSummaryRepository.findBySellerId(sellerId)
                .map(RevenueResponse::from)
                .orElseGet(RevenueResponse::empty);
    }

    public List<SellerTeamResponse> teams(MemberUserDetails principal) {
        requireSeller(principal);
        List<GroupBuyTeam> teams =
                groupBuyTeamRepository.findAllBySellerIdWithProduct(principal.getMember().getId());
        Map<Long, List<String>> participantNamesByTeamId = participantNamesByTeamId(teams);
        return teams.stream()
                .map(team -> SellerTeamResponse.from(
                        team,
                        participantNamesByTeamId.getOrDefault(team.getId(), List.of())))
                .toList();
    }

    // 팀마다 참여자를 따로 조회하면 팀 수만큼 쿼리가 나간다. 한 번에 받아 팀별로 묶는다.
    private Map<Long, List<String>> participantNamesByTeamId(List<GroupBuyTeam> teams) {
        if (teams.isEmpty()) {
            // 빈 목록을 그대로 넘기면 IN () 형태가 되어 DB에 따라 문법 오류가 난다.
            return Map.of();
        }
        List<Long> teamIds = teams.stream().map(GroupBuyTeam::getId).toList();
        return teamParticipationRepository.findAllByTeamIdsWithMember(teamIds).stream()
                .collect(Collectors.groupingBy(
                        participation -> participation.getTeam().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                participation -> participation.getMember().getName(),
                                Collectors.toList())));
    }

    public NotificationListResponse notifications(MemberUserDetails principal, int page, int size) {
        requireSeller(principal);
        validatePageRequest(page, size);
        Long memberId = principal.getMember().getId();
        return NotificationListResponse.of(
                notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(page, size)),
                notificationRepository.countByMemberIdAndIsReadFalse(memberId));
    }

    // 클래스 기본이 @Transactional(readOnly = true)라, 실제 쓰기가 필요한 이 두 메서드는 명시적으로
    // 덮어써야 한다(BuyerMypageService와 동일한 이유).
    @Transactional
    public void markNotificationAsRead(MemberUserDetails principal, Long notificationId) {
        requireSeller(principal);
        notificationService.markAsRead(principal, notificationId);
    }

    @Transactional
    public void markAllNotificationsAsRead(MemberUserDetails principal) {
        requireSeller(principal);
        notificationService.markAllAsRead(principal);
    }

    // 내가 등록한 상품에 대한 환불 요청 전체(대기/승인/거절 포함) — 승인/거절 액션 자체는
    // RefundRequestController가 담당한다(마이페이지 컨트롤러는 조회만).
    public List<RefundRequestResponse> refundRequests(MemberUserDetails principal) {
        requireSeller(principal);
        return refundRequestRepository.findAllBySellerIdWithPaymentAndProduct(principal.getMember().getId()).stream()
                .map(RefundRequestResponse::from)
                .toList();
    }

    private void requireSeller(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    // BuyerMypageService와 동일한 이유(page<0/size<1이면 PageRequest.of가 던지는 IllegalArgumentException이
    // 500으로 새던 버그, docs/dev/ongoing/notification-pagination-param-validation.md).
    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
