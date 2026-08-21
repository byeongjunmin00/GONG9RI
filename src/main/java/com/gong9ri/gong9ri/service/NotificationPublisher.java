package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.NotificationType;
import com.gong9ri.gong9ri.event.NotificationRequestedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 알림 문구와 이동 링크를 한곳에서 정하고 {@code NotificationRequestedEvent}로 발행한다.
 *
 * 이 클래스가 있는 이유는 두 가지다.
 * <ul>
 *   <li><b>문구/링크 규칙을 한 파일에 모은다.</b> 알림 8종의 문구가 각 도메인 서비스에 흩어지면
 *       톤이 제각각이 되고 어떤 알림이 있는지 한눈에 볼 수 없다.</li>
 *   <li><b>호출부를 한 줄로 만든다.</b> 각 도메인 서비스(문의·리뷰·결제·환불·공구팀)는 자기 일을 한 뒤
 *       {@code notificationPublisher.inquiryCreated(...)} 한 줄만 부르면 된다 — 알림이 어떻게 저장되는지,
 *       어느 트랜잭션에서 도는지는 몰라도 된다.</li>
 * </ul>
 *
 * 발행만 하고 저장은 하지 않는다 — 실제 INSERT는 커밋 이후에 {@code NotificationRequestedEventListener}
 * → {@code NotificationService}가 한다. 그래서 여기서 발행해도 원인 작업이 롤백되면 알림도 남지 않는다.
 *
 * <p><b>자기 행동에 자기가 알림받지 않는다</b> — 판매자가 자기 상품에 뭔가 한 경우(예: 판매자 본인이
 * 자기 상품을 구매)처럼 수신자와 행위자가 같으면 발행하지 않는다. 각 메서드가 actor를 받아서 걸러낸다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    // 문구에 상품명이 들어가지만 message 컬럼(255자)을 넘지 않는다 — 상품명이 최대 100자
    // (Product.name의 length=100)라, 가장 긴 문구도 상품명 100 + 고정 문구 30자 남짓이다.
    // 상품명 제한을 늘릴 일이 생기면 이 계산을 다시 확인해야 한다.
    private static final String BUYER_MYPAGE_URL = "/buyer/mypage.html";
    private static final String SELLER_MYPAGE_URL = "/seller/mypage.html";

    private final ApplicationEventPublisher eventPublisher;

    // ---------- 공구팀 ----------

    /** 공구팀이 정원을 채워 성사됨 — 참여자 전원과 판매자가 각각 다른 문구로 받는다. */
    public void teamSucceeded(List<Long> participantMemberIds, Long sellerId, Long teamId, Long productId,
            String productName) {
        publish(participantMemberIds, NotificationType.TEAM_SUCCESS,
                "참여하신 공구 '" + productName + "'가 성사되었습니다.", teamId, productUrl(productId));
        publish(List.of(sellerId), NotificationType.TEAM_SUCCESS,
                "등록하신 상품 '" + productName + "'의 공구가 성사되었습니다.", teamId, SELLER_MYPAGE_URL);
    }

    // ---------- 문의 ----------

    /** 상품에 문의가 등록됨 — 판매자에게. 판매자가 자기 상품에 문의를 남긴 경우는 보내지 않는다. */
    public void inquiryCreated(Long sellerId, Long askerId, Long productId, String productName) {
        if (sellerId.equals(askerId)) {
            return;
        }
        publish(List.of(sellerId), NotificationType.INQUIRY_CREATED,
                "'" + productName + "'에 새 문의가 등록되었습니다.", null, productUrl(productId));
    }

    /** 문의에 답변이 등록됨 — 문의 작성자에게. 작성자 본인이 답변한 경우는 보내지 않는다. */
    public void inquiryAnswered(Long askerId, Long answererId, Long productId, String productName) {
        if (askerId.equals(answererId)) {
            return;
        }
        publish(List.of(askerId), NotificationType.INQUIRY_ANSWERED,
                "'" + productName + "' 문의에 답변이 등록되었습니다.", null, productUrl(productId));
    }

    // ---------- 고객센터 상담 (support/chat) ----------

    /**
     * 상담에 새 메시지가 도착함 — 관리자 전원에게.
     *
     * <p>관리자가 대시보드에 들어가 보지 않는 한 상담이 온 걸 알 수 없다는 문제를 푼다
     * (2026-08-21 사용자 리포트). 링크는 상담 관리 화면으로 보낸다.
     */
    public void supportMessageReceived(List<Long> adminIds, String senderName) {
        if (adminIds.isEmpty()) {
            return;
        }
        publish(adminIds, NotificationType.SUPPORT_MESSAGE_RECEIVED,
                senderName + "님이 상담 메시지를 보냈습니다.", null, "/admin/support.html");
    }

    // ---------- 결제 ----------

    /** 상품이 결제됨 — 판매자에게. 판매자가 자기 상품을 산 경우는 보내지 않는다. */
    public void paymentReceived(Long sellerId, Long buyerId, String productName, Integer amount) {
        if (sellerId.equals(buyerId)) {
            return;
        }
        publish(List.of(sellerId), NotificationType.PAYMENT_RECEIVED,
                "'" + productName + "' 상품이 " + amount + "원에 결제되었습니다.", null, SELLER_MYPAGE_URL);
    }

    // ---------- 리뷰 ----------

    /** 상품에 리뷰가 등록됨 — 판매자에게. */
    public void reviewCreated(Long sellerId, Long reviewerId, Long productId, String productName, Integer rating) {
        if (sellerId.equals(reviewerId)) {
            return;
        }
        publish(List.of(sellerId), NotificationType.REVIEW_CREATED,
                "'" + productName + "'에 " + rating + "점 리뷰가 등록되었습니다.", null, productUrl(productId));
    }

    // ---------- 환불 요청 ----------

    /** 환불 요청이 접수됨 — 판매자에게(승인/거절 처리가 필요하다). */
    public void refundRequested(Long sellerId, Long requesterId, String productName) {
        if (sellerId.equals(requesterId)) {
            return;
        }
        publish(List.of(sellerId), NotificationType.REFUND_REQUESTED,
                "'" + productName + "' 환불 요청이 접수되었습니다. 확인해주세요.", null, SELLER_MYPAGE_URL);
    }

    /** 환불 요청이 승인됨 — 요청한 구매자에게. */
    public void refundRequestApproved(Long requesterId, String productName) {
        publish(List.of(requesterId), NotificationType.REFUND_REQUEST_APPROVED,
                "'" + productName + "' 환불 요청이 승인되었습니다.", null, BUYER_MYPAGE_URL);
    }

    /** 환불 요청이 거절됨 — 요청한 구매자에게. */
    public void refundRequestRejected(Long requesterId, String productName) {
        publish(List.of(requesterId), NotificationType.REFUND_REQUEST_REJECTED,
                "'" + productName + "' 환불 요청이 거절되었습니다.", null, BUYER_MYPAGE_URL);
    }

    // ---------- 배송 ----------

    /** 판매자가 주문의 배송 단계를 바꿈 — 그 주문의 구매자에게. 판매자가 자기 상품을 산 경우는 보내지 않는다. */
    public void shipmentUpdated(Long sellerId, Long buyerId, String productName, String shipmentStatusLabel) {
        if (sellerId.equals(buyerId)) {
            return;
        }
        publish(List.of(buyerId), NotificationType.SHIPMENT_UPDATED,
                "'" + productName + "' 주문의 배송 상태가 '" + shipmentStatusLabel + "'(으)로 변경되었습니다.",
                null, BUYER_MYPAGE_URL);
    }

    // ---------- 내부 ----------

    private String productUrl(Long productId) {
        return "/product.html?id=" + productId;
    }

    private void publish(List<Long> memberIds, NotificationType type, String message, Long teamId, String linkUrl) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new NotificationRequestedEvent(memberIds, type, message, teamId, linkUrl));
    }
}
