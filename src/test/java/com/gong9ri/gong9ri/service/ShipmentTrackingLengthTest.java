package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ShipmentUpdateRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.ShipmentStatus;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 송장번호·택배사에 <b>컬럼 길이를 넘는 값</b>이 들어왔을 때 400으로 거절되는지 고정한다(007 후속).
 *
 * <p>{@code tracking_number}/{@code tracking_carrier}는 VARCHAR(50)인데 DTO에 길이 제한이 없으면,
 * 긴 값이 검증을 통과해 <b>커밋 시점에 MySQL data truncation</b>으로 터진다 — 사용자 입력 문제인데
 * 500이 나가는 것으로, 업로드 용량 초과가 500을 내던 것과 같은 부류다.
 *
 * <p>이 검증은 <b>{@code @Transactional} 테스트로는 확인할 수 없다.</b> 롤백만 하면 flush가 일어나지
 * 않아 truncation이 드러나지 않기 때문 — 그래서 여기서는 실제로 커밋시킨다.
 */
@SpringBootTest
class ShipmentTrackingLengthTest {

    @Autowired
    private SellerMypageService sellerMypageService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private Validator validator;

    private static final String SELLER_USERNAME = "trackLenSeller";
    private static final String BUYER_USERNAME = "trackLenBuyer";

    private Long paymentId;
    private Long sellerId;

    /**
     * 이 테스트는 <b>실제로 커밋</b>하므로 롤백이 뒷정리를 해주지 않는다. 앞뒤로 모두 정리하는 이유는,
     * 실행이 중간에 죽어 계정이 남으면 {@code username} 유니크 제약 때문에 <b>다음 실행이 통째로</b>
     * 깨지기 때문이다(실제로 한 번 겪었다).
     */
    @BeforeEach
    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            memberRepository.findByUsername(SELLER_USERNAME).ifPresent(seller -> {
                paymentRepository.findAllBySellerIdWithProductAndMemberAndTeam(seller.getId())
                        .forEach(payment -> paymentRepository.deleteById(payment.getId()));
                productRepository.findAllBySellerIdOrderByCreatedAtDesc(seller.getId())
                        .forEach(product -> productRepository.deleteById(product.getId()));
            });
        });
        for (String username : new String[] {BUYER_USERNAME, SELLER_USERNAME}) {
            deleteMemberWithNotifications(username);
        }
    }

    /**
     * 배송 상태 변경 알림은 {@code AFTER_COMMIT + @Async}라 <b>이 테스트가 끝난 뒤에 INSERT될 수 있다.</b>
     * 알림을 한 번만 지우고 곧바로 회원을 지우면 그 사이에 도착한 알림이 FK로 삭제를 막는다 — 상담 보안
     * 테스트가 CI에서만 깨졌던 것과 같은 경합이고, 여기서는 로컬에서도 재현됐다. 그래서 재시도한다.
     */
    private void deleteMemberWithNotifications(String username) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                boolean done = transactionTemplate.execute(status ->
                        memberRepository.findByUsername(username).map(member -> {
                            notificationRepository.findAllByMemberIdOrderByCreatedAtDesc(member.getId())
                                    .forEach(n -> notificationRepository.deleteById(n.getId()));
                            memberRepository.deleteById(member.getId());
                            memberRepository.flush();
                            return true;
                        }).orElse(true));
                if (Boolean.TRUE.equals(done)) {
                    return;
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Test
    @DisplayName("50자를 넘는 송장번호는 DTO 검증에서 걸러진다 — DB까지 가면 truncation 500이 된다")
    void tooLongTrackingNumber_rejectedByValidation() {
        String tooLong = "1".repeat(200);

        var violations = validator.validate(
                new ShipmentUpdateRequest(ShipmentStatus.IN_TRANSIT, "CJ대한통운", tooLong));

        assertEquals(1, violations.size(),
                "송장번호 길이 제한이 없으면 검증을 통과해 커밋 시점 data truncation(500)이 된다");
    }

    @Test
    @DisplayName("50자를 넘는 택배사명도 DTO 검증에서 걸러진다")
    void tooLongTrackingCarrier_rejectedByValidation() {
        var violations = validator.validate(
                new ShipmentUpdateRequest(ShipmentStatus.IN_TRANSIT, "가".repeat(200), "123456789012"));

        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("서비스가 실제 커밋까지 갔을 때 50자 이하 송장번호는 정상 저장된다")
    void normalTrackingNumber_persisted() {
        setUpOrder();

        transactionTemplate.executeWithoutResult(status -> sellerMypageService.updateShipment(
                new MemberUserDetails(memberRepository.findById(sellerId).orElseThrow()),
                paymentId,
                new ShipmentUpdateRequest(ShipmentStatus.IN_TRANSIT, "CJ대한통운", "1".repeat(50))));

        Payment saved = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(ShipmentStatus.IN_TRANSIT, saved.getShipmentStatus());
        assertEquals("1".repeat(50), saved.getTrackingNumber());
    }

    private void setUpOrder() {
        transactionTemplate.executeWithoutResult(status -> {
            Member seller = memberRepository.save(new Member(
                    "trackLenSeller", "pw", "판매자", "tracklen-seller@test.com", Role.SELLER));
            Member buyer = memberRepository.save(new Member(
                    "trackLenBuyer", "pw", "구매자", "tracklen-buyer@test.com", Role.BUYER));
            Product product = productRepository.save(new Product(
                    seller, "송장 길이 테스트 상품", "테스트", 10000, 5, null));
            Payment payment = paymentRepository.save(new Payment(buyer, product, null, 10000));
            sellerId = seller.getId();
            paymentId = payment.getId();
        });
    }
}
