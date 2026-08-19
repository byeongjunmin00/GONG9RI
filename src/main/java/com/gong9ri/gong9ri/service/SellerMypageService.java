package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.NotificationListResponse;
import com.gong9ri.gong9ri.dto.NotificationResponse;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.dto.SellerProductResponse;
import com.gong9ri.gong9ri.dto.SellerTeamResponse;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.util.List;
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
    private final NotificationService notificationService;

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
        return groupBuyTeamRepository.findAllBySellerIdWithProduct(principal.getMember().getId()).stream()
                .map(SellerTeamResponse::from)
                .toList();
    }

    public NotificationListResponse notifications(MemberUserDetails principal, int page, int size) {
        requireSeller(principal);
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
}
