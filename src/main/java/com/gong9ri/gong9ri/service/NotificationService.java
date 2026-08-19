package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Notification;
import com.gong9ri.gong9ri.entity.NotificationType;
import com.gong9ri.gong9ri.event.TeamRefundedEvent;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 완료 이벤트({@code TeamRefundedEvent})를 받아 그 팀에서 환불된 결제의 구매자 전원 + 상품 판매자
 * 각각에게 Notification 레코드를 하나씩 생성한다(docs/dev/ongoing/refund-event-messaging.md). 읽음 처리
 * (2026-08-19 추가, 알림 벨 UI)도 여기서 같이 담당 — 구매자/판매자 마이페이지 양쪽에서 공용으로 쓴다.
 * 알림은 역할과 무관하게 회원 단위라 WishlistService처럼 역할 제약을 여기서 걸지 않는다 — 역할 게이트는
 * 호출부인 {@code Buyer,SellerMypageService}가 각자 requireBuyer/requireSeller로 이미 하고, 여기서는
 * "이 알림이 진짜 이 회원 것인지"(소유권)만 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String BUYER_REFUND_MESSAGE = "참여하신 공구팀이 미성사되어 환불 처리되었습니다.";
    private static final String SELLER_REFUND_MESSAGE = "등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다.";

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;

    // member/team은 FK로만 쓰이고 필드를 읽지 않으므로 getReferenceById로 불필요한 SELECT를 피한다.
    //
    // REQUIRES_NEW인 이유: 이 메서드는 TeamRefundedEventListener(@TransactionalEventListener(AFTER_COMMIT))가
    // 원본 트랜잭션(TeamDeadlineService.processDeadline)의 commit 직후 동기 콜백으로 호출한다. 스프링 공식 문서가
    // 명시하는 대로("you can no longer append any operation to the original transaction unless you start a new,
    // independent transaction"), 이 시점엔 원본 트랜잭션의 리소스(ConnectionHolder)가 아직 스레드에 정리되지
    // 않고 남아있을 수 있어 propagation을 기본값(REQUIRED)으로 두면 새 트랜잭션을 시작하는 대신 "이미 커밋된"
    // 그 리소스에 조인해버려 이 메서드의 INSERT가 실제로 커밋되지 않는 채로 사라진다(실제로 겪은 버그 —
    // docs/logs/notification/refund-alert 참고). REQUIRES_NEW로 물리적으로 분리된 새 트랜잭션임을 보장한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createTeamRefundedNotifications(TeamRefundedEvent event) {
        GroupBuyTeam relatedTeam = groupBuyTeamRepository.getReferenceById(event.teamId());

        // 결제 건수만큼이 아니라 구매자당 알림 1건 — 같은 구매자가 결제를 여러 건 했더라도 중복 제거한다.
        Set<Long> distinctBuyerIds = new LinkedHashSet<>(event.buyerMemberIds());
        for (Long buyerId : distinctBuyerIds) {
            Member buyer = memberRepository.getReferenceById(buyerId);
            notificationRepository.save(
                    new Notification(buyer, NotificationType.TEAM_REFUNDED, BUYER_REFUND_MESSAGE, relatedTeam));
        }

        Member seller = memberRepository.getReferenceById(event.sellerId());
        notificationRepository.save(
                new Notification(seller, NotificationType.TEAM_REFUNDED, SELLER_REFUND_MESSAGE, relatedTeam));

        log.info("공구팀 환불 알림 생성 완료: teamId={}, sellerId={}, buyerCount={}",
                event.teamId(), event.sellerId(), distinctBuyerIds.size());
    }

    // 클래스 기본이 @Transactional(readOnly = true)라 명시적으로 덮어쓴다.
    @Transactional
    public void markAsRead(MemberUserDetails principal, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getMember().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(MemberUserDetails principal) {
        notificationRepository.markAllAsReadByMemberId(principal.getMember().getId());
    }
}
