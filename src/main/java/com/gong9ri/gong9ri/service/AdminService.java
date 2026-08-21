package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.AdminDashboardResponse;
import com.gong9ri.gong9ri.dto.AdminMemberPageResponse;
import com.gong9ri.gong9ri.dto.AdminMemberResponse;
import com.gong9ri.gong9ri.dto.AdminRefundPageResponse;
import com.gong9ri.gong9ri.dto.NotificationListResponse;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.AiSuggestionLogRepository;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.InquiryRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자(admin) 기능 — docs/dev/admin/design.md.
 *
 * <p>이 프로젝트의 role 검사는 전부 서비스단 {@code requireXxx()} 가드로 하고(SecurityConfig엔 role
 * 기반 URL 매처가 없음, ProductService.requireSeller() 등과 동일 패턴), 여기서도 그대로 따른다.
 *
 * <p>회원 "삭제"는 하드 삭제를 함부로 허용하지 않는다 — Member를 참조하는 테이블이 많아(Product/
 * Payment/Review/GroupBuyTeam/TeamParticipation/Wishlist/Inquiry/RefundRequest/ChatSession), 이
 * 테이블들 중 하나라도 이 회원을 참조하는 행이 있으면 삭제를 막고 정지(suspend)로 유도한다. 정말 아무
 * 흔적도 없는 계정만 실제로 지우고, 그때도 leaf 데이터(Notification/AiSuggestionLog/
 * SellerRevenueSummary — 다른 테이블이 참조하지 않음)는 함께 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final MemberRepository memberRepository;
    private final ProductService productService;
    private final NotificationService notificationService;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final WishlistRepository wishlistRepository;
    private final InquiryRepository inquiryRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final NotificationRepository notificationRepository;
    private final AiSuggestionLogRepository aiSuggestionLogRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    public AdminMemberPageResponse members(MemberUserDetails principal, int page, int size) {
        return members(principal, page, size, null, null, null);
    }

    public AdminMemberPageResponse members(MemberUserDetails principal, int page, int size, String search, Role role, Boolean suspended) {
        requireAdmin(principal);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Member> members = memberRepository.findAllForAdmin(pageable, search, role, suspended);

        List<Long> memberIds = members.getContent().stream().map(Member::getId).toList();
        Map<Long, Integer> purchaseMap = toCountMap(paymentRepository.countPaymentsByMemberIds(memberIds));
        Map<Long, Integer> teamMap = toCountMap(teamParticipationRepository.countParticipationsByMemberIds(memberIds));
        Map<Long, Integer> productMap = toCountMap(productRepository.countProductsBySellerIds(memberIds));

        List<AdminMemberResponse> content = members.getContent().stream()
                .map(m -> AdminMemberResponse.of(
                        m,
                        purchaseMap.getOrDefault(m.getId(), 0),
                        teamMap.getOrDefault(m.getId(), 0),
                        productMap.getOrDefault(m.getId(), 0)
                ))
                .toList();
        return AdminMemberPageResponse.of(members, content);
    }

    private Map<Long, Integer> toCountMap(List<Object[]> results) {
        if (results == null || results.isEmpty()) {
            return Map.of();
        }
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (v1, v2) -> v1
                ));
    }

    @Transactional
    public void suspendMember(MemberUserDetails principal, Long memberId) {
        requireAdmin(principal);
        requireNotSelf(principal, memberId);
        Member target = findMember(memberId);
        target.suspend();
        log.info("관리자 회원 정지: adminId={}, memberId={}", principal.getMember().getId(), memberId);
    }

    @Transactional
    public void unsuspendMember(MemberUserDetails principal, Long memberId) {
        requireAdmin(principal);
        Member target = findMember(memberId);
        target.unsuspend();
        log.info("관리자 회원 정지 해제: adminId={}, memberId={}", principal.getMember().getId(), memberId);
    }

    @Transactional
    public void deleteMember(MemberUserDetails principal, Long memberId) {
        requireAdmin(principal);
        requireNotSelf(principal, memberId);
        Member target = findMember(memberId);

        if (hasActivity(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVITY);
        }

        // 다른 테이블이 참조하지 않는 leaf 데이터부터 정리한 뒤 회원을 지운다.
        notificationRepository.deleteByMemberId(memberId);
        aiSuggestionLogRepository.deleteBySeller_Id(memberId);
        sellerRevenueSummaryRepository.deleteBySellerId(memberId);
        memberRepository.delete(target);
        log.info("관리자 회원 삭제: adminId={}, memberId={}", principal.getMember().getId(), memberId);
    }

    // 상품 삭제는 ProductService에 맡긴다 — 삭제 정책(결제·팀·리뷰가 있으면 거절)과 캐시 무효화가
    // 판매자 삭제와 완전히 같아야 하는데, 여기서 따로 구현하면 한쪽만 고쳐지는 일이 생긴다.
    @Transactional
    public void deleteProduct(MemberUserDetails principal, Long productId, boolean force) {
        requireAdmin(principal);
        productService.deleteByAdmin(principal, productId, force);
    }

    // 숨김은 "되돌릴 수 있는 정리"다 — 결제·리뷰가 붙어 지울 수 없는 상품을 목록에서 치울 때 쓴다.
    @Transactional
    public void setProductHidden(MemberUserDetails principal, Long productId, boolean hidden) {
        requireAdmin(principal);
        productService.setHiddenByAdmin(principal, productId, hidden);
    }

    public ProductPageResponse products(MemberUserDetails principal, int page, int size) {
        return products(principal, page, size, null, null);
    }

    public ProductPageResponse products(MemberUserDetails principal, int page, int size, String search, String status) {
        requireAdmin(principal);
        return productService.listForAdmin(page, size, search, status);
    }

    // 관리자 알림(support/chat) — 구매자·판매자와 같은 알림 테이블을 쓰지만, 조회 경로가 역할별로
    // 갈라져 있어(/api/{buyer,seller}/mypage/notifications) 관리자는 알림벨을 아예 쓸 수 없었다.
    // 상담이 와도 대시보드에 직접 들어가야만 알 수 있던 문제(2026-08-21 사용자 리포트)를 여기서 푼다.
    public NotificationListResponse notifications(MemberUserDetails principal, int page, int size) {
        requireAdmin(principal);
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Long memberId = principal.getMember().getId();
        return NotificationListResponse.of(
                notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(page, size)),
                notificationRepository.countByMemberIdAndIsReadFalse(memberId));
    }

    @Transactional
    public void markNotificationAsRead(MemberUserDetails principal, Long notificationId) {
        requireAdmin(principal);
        notificationService.markAsRead(principal, notificationId);
    }

    @Transactional
    public void markAllNotificationsAsRead(MemberUserDetails principal) {
        requireAdmin(principal);
        notificationService.markAllAsRead(principal);
    }

    public AdminDashboardResponse dashboard(MemberUserDetails principal) {
        requireAdmin(principal);
        return new AdminDashboardResponse(
                memberRepository.count(),
                memberRepository.countByRole(Role.BUYER),
                memberRepository.countByRole(Role.SELLER),
                productRepository.count(),
                paymentRepository.count(),
                refundRequestRepository.countByStatus(RefundRequestStatus.PENDING)
        );
    }

    public AdminRefundPageResponse refundRequests(MemberUserDetails principal, int page, int size,
            RefundRequestStatus status) {
        requireAdmin(principal);
        Pageable pageable = PageRequest.of(page, size);
        // 파생 쿼리에서 fetch join 쿼리로 바꿨다 — payment/product/requester가 전부 LAZY라
        // 한 행마다 세 번씩 더 나가고 있었다(응답에 요청자를 추가하며 한 번 더 늘어날 참이었다).
        return AdminRefundPageResponse.of(refundRequestRepository.findAllForAdmin(status, pageable));
    }

    private boolean hasActivity(Long memberId) {
        return productRepository.existsBySeller_Id(memberId)
                || paymentRepository.existsByMemberId(memberId)
                || reviewRepository.existsByMemberId(memberId)
                || groupBuyTeamRepository.existsByLeader_Id(memberId)
                || teamParticipationRepository.existsByMember_Id(memberId)
                || wishlistRepository.existsByMember_Id(memberId)
                || inquiryRepository.existsByMember_IdOrAnsweredBy_Id(memberId, memberId)
                || refundRequestRepository.existsByRequester_Id(memberId)
                || chatSessionRepository.existsByBuyer_Id(memberId);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    // 관리자가 실수로 자기 자신을 정지/삭제해서 스스로를 잠그는 걸 막는다.
    private void requireNotSelf(MemberUserDetails principal, Long memberId) {
        if (principal.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireAdmin(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
