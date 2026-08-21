package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_member", columnList = "member_id"),
        @Index(name = "idx_team_status", columnList = "team_id, status"),
        @Index(name = "idx_product", columnList = "product_id"),
        // PortOne 웹훅이 알려주는 pgPaymentId로 우리 결제 건을 역으로 찾을 때, 취소 API 호출 시 어떤
        // pgPaymentId를 보낼지 결정할 때 쓴다. MySQL은 UNIQUE 인덱스에서 NULL을 서로 다른 값으로 취급하므로
        // (레거시/테스트용 4-arg 생성자로 만든 pgPaymentId 없는 행이 여러 개 있어도) 유니크 제약과 충돌하지 않는다.
        @Index(name = "idx_pg_payment_id", columnList = "pg_payment_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private GroupBuyTeam team;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // PortOne에 보낸 가맹점 채번 결제 식별자(merchant paymentId) — 취소 API 호출 대상 특정, 웹훅이
    // 가리키는 결제 건 역조회에 쓴다. 4-arg 생성자(레거시/테스트에서 "이미 확정된 결제"를 직접 만들 때)로
    // 만든 행은 null일 수 있다(docs/dev/payment/portone/design.md).
    @Column(name = "pg_payment_id", length = 64)
    private String pgPaymentId;

    // 결제 요청 접수 시각(레코드 생성 시각). PENDING으로 시작하므로 "실제 승인 시각"과 항상 같지는 않다
    // — 확정(PAID) 시각은 별도 컬럼으로 관리하지 않는다(이 스코프에서는 필요성이 낮다고 판단).
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime paidAt;

    // 판매자가 직접 조작하는 배송 단계(007) — 기본값은 상품 준비중. REFUNDED/RECRUITING/FAILED
    // 상태인 주문(=배송 대상이 아니거나 취소된 주문)은 이 값을 바꿀 수 없다(SellerMypageService에서 검증).
    //
    // columnDefinition에 DEFAULT를 명시한다 — 안 그러면 로컬에서 실측한 대로 이 컬럼이 처음 추가될 때
    // (ddl-auto=update의 ALTER TABLE ADD COLUMN) 기존 행들이 자바 필드 초기값(PRODUCT_PREPARING)이
    // 아니라 MySQL이 ENUM 컬럼에 자동으로 붙이는 암묵적 기본값(정의 순서상 첫 번째 값 — Hibernate가
    // enum을 알파벳순으로 나열해서 실제로는 "DELIVERED"였다)으로 채워진다. 이미 배송 완료된 적 없는
    // 옛 결제들이 전부 "배송완료"로 잘못 표시되는 걸 로컬 DESCRIBE로 직접 확인하고 이 컬럼 정의를 고쳤다.
    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) DEFAULT 'PRODUCT_PREPARING'")
    private ShipmentStatus shipmentStatus = ShipmentStatus.PRODUCT_PREPARING;

    @Column(name = "tracking_carrier", length = 50)
    private String trackingCarrier;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    /**
     * 이미 확정된(PAID) 결제를 직접 만드는 생성자 — PortOne 연동 이전부터 있던 생성자로, 지금은
     * 테스트에서 "이미 결제완료된 이력"을 사전 세팅할 때만 쓴다(pgPaymentId는 null). 실제 서비스 흐름
     * (PaymentService.create)은 아래 5-arg 생성자로 PENDING 결제를 만든 뒤 서버 재검증을 거쳐 확정한다.
     */
    public Payment(Member member, Product product, GroupBuyTeam team, Integer amount) {
        this.member = member;
        this.product = product;
        this.team = team;
        this.amount = amount;
        this.status = PaymentStatus.PAID;
    }

    // 실제 결제 요청 접수(PaymentService.create) — PortOne 결제창을 열기 전 단계라 PENDING으로 시작한다.
    public Payment(Member member, Product product, GroupBuyTeam team, Integer amount, String pgPaymentId) {
        this.member = member;
        this.product = product;
        this.team = team;
        this.amount = amount;
        this.pgPaymentId = pgPaymentId;
        this.status = PaymentStatus.PENDING;
    }

    // 서버가 PortOne API 재조회로 승인·금액 일치를 확인했을 때만 호출한다(PaymentService.applyVerificationResult).
    public void confirm() {
        if (this.status == PaymentStatus.PENDING) {
            this.status = PaymentStatus.PAID;
        }
    }

    // 서버 재조회 결과 PortOne이 최종 실패로 응답했을 때 호출한다.
    public void fail() {
        if (this.status == PaymentStatus.PENDING) {
            this.status = PaymentStatus.FAILED;
        }
    }

    // PortOne 결제취소 API가 REQUESTED(비동기 처리 중)로 응답했을 때 — 웹훅(Transaction.Cancelled)의
    // 최종 확정을 기다리는 중간 상태로 전환한다.
    public void markRefundPending() {
        if (this.status == PaymentStatus.PAID) {
            this.status = PaymentStatus.REFUND_PENDING;
        }
    }

    // PortOne 취소가 실제로 완료됐음을 확인했을 때만 호출한다(즉시 SUCCEEDED 또는 웹훅 Transaction.Cancelled로 확정).
    public void refund() {
        if (this.status == PaymentStatus.PAID || this.status == PaymentStatus.REFUND_PENDING) {
            this.status = PaymentStatus.REFUNDED;
        }
    }

    // 본인 결제 확인 — PaymentService/RefundRequestService가 각자 따로 구현하던 동일한 검증을 여기로
    // 통합했다(둘 다 "이 결제의 구매자 == 이 memberId"만 확인).
    public boolean isOwnedBy(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    // 판매자가 배송 단계/택배사/송장번호를 갱신한다 — "적용 대상인지"(REFUNDED 등 제외)와 "배송중/배송완료엔
    // 송장번호 필수"는 서비스 레이어(SellerMypageService)가 미리 검증하고 호출한다(다른 엔티티들과 동일하게
    // 이 클래스는 검증 없이 값만 반영, docs/dev/mypage/view/changes/007-*.md).
    public void updateShipment(ShipmentStatus shipmentStatus, String trackingCarrier, String trackingNumber) {
        this.shipmentStatus = shipmentStatus;
        this.trackingCarrier = trackingCarrier;
        this.trackingNumber = trackingNumber;
    }
}
