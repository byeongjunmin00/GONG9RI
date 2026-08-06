package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.SellerRevenueSummary;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.RevenueSummaryProjection;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * seller_revenue_summary 1회성 백필(docs/db/seller_revenue_summary.md,
 * docs/dev/ongoing/seller-revenue-summary-upsert-fix.md) — {@code SellerRevenueSummaryRepository.incrementPaid}가
 * upsert로 바뀐 뒤부터는 새 결제가 요약 행을 스스로 만들지만, 그 전환 이전부터 존재하던 결제 이력이 있는
 * 판매자는 아직 요약 행이 없을 수 있다. 이건 "조회마다 신경 쓸 문제"가 아니라 "배포 시점에 한 번만
 * 처리하는 문제"라, {@code SellerMypageService.revenue()}(조회 경로)에서는 절대 호출하지 않는다 —
 * 호출부는 {@code SellerRevenueSummaryBackfillRunner}(기본 비활성, 배포 시 opt-in 실행)뿐이다.
 *
 * 팀 마감 스캔(TeamDeadlineService)과 같은 이유로, 대상 판매자 전체를 하나의 트랜잭션으로 묶지 않고
 * 판매자 1명당 트랜잭션을 분리한다(대량 백필 중 하나가 실패해도 나머지 진행에 영향 없게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerRevenueSummaryBackfillService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;

    public int backfillMissingSummaries() {
        List<Long> sellerIdsWithPayments = paymentRepository.findDistinctSellerIdsWithPayments();
        int createdCount = 0;
        for (Long sellerId : sellerIdsWithPayments) {
            if (backfillOneIfMissing(sellerId)) {
                createdCount++;
            }
        }
        log.info("seller_revenue_summary 백필 스캔 완료: 결제이력있는판매자수={}, 신규생성행수={}",
                sellerIdsWithPayments.size(), createdCount);
        return createdCount;
    }

    // 판매자 1명 단위 백필 — 이미 요약 행이 있으면 건드리지 않는다(정상 upsert 경로로 이미 최신 상태라고
    // 간주). 동시에 이 판매자의 "첫 결제"가 들어와 incrementPaid(upsert)가 막 행을 만든 경우와 경합할
    // 수 있는데, unique(seller_id) 제약으로 하나만 insert에 성공하고 나머지는 조용히 건너뛴다
    // (team/join-atomic의 DataIntegrityViolationException 처리와 같은 패턴) — 어느 쪽이 이겨도 값은
    // 정확하다(이 메서드가 이겨도 recomputed는 그 시점 payment 테이블 기준 실제 값이므로).
    @Transactional
    public boolean backfillOneIfMissing(Long sellerId) {
        if (sellerRevenueSummaryRepository.findBySellerId(sellerId).isPresent()) {
            return false;
        }
        RevenueSummaryProjection recomputed = paymentRepository.findRevenueSummaryBySellerId(sellerId);
        Member seller = memberRepository.getReferenceById(sellerId);
        SellerRevenueSummary summary = new SellerRevenueSummary(
                seller, recomputed.getTotalRevenue(), recomputed.getPaidCount(), recomputed.getRefundedCount());
        try {
            sellerRevenueSummaryRepository.save(summary);
            log.info("seller_revenue_summary 백필 생성: sellerId={}, totalRevenue={}, paidCount={}, refundedCount={}",
                    sellerId, recomputed.getTotalRevenue(), recomputed.getPaidCount(), recomputed.getRefundedCount());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("seller_revenue_summary 백필 중 경합 발생(이미 다른 경로로 생성됨), 건너뜀: sellerId={}", sellerId);
            return false;
        }
    }
}
