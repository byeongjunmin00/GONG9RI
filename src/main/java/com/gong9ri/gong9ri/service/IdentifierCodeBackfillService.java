package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.identifier.IdentifierCodeFormatter;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원번호 · 상품코드 · 주문번호 · 공구팀 번호 1회성 백필(admin-identifier-codes,
 * {@code docs/dev/ongoing/admin-identifier-codes.md}).
 *
 * <p>이 컬럼들은 {@code Member}/{@code Product}/{@code Payment}/{@code GroupBuyTeam}에 새로
 * 추가된 nullable 컬럼이다 — 신규 생성 경로(가입/상품등록/결제생성/팀신설)는 각 서비스가 저장 직후
 * 바로 채번하지만, 이 컬럼이 생기기 <b>이전에</b> 만들어진 기존 행은 값이 없다. 회원마다(상품마다,
 * 결제마다, 팀마다) 값이 다른 컬럼이라 {@code @ColumnDefault} 같은 상수 기본값으로 한 번에 채울 수
 * 없어({@code Member.emailVerified}식 마이그레이션이 여기선 안 통함), 이 서비스가 행 단위로
 * PK(+결제는 paidAt)를 코드로 변환해 채운다.
 *
 * <p>{@code seller_revenue_summary} 백필({@link SellerRevenueSummaryBackfillService})과 같은 이유로,
 * 대상 전체를 하나의 트랜잭션으로 묶지 않고 <b>행 1개당 트랜잭션을 분리</b>한다(대량 백필 중 하나가
 * 실패해도 나머지 진행에 영향 없게). 조회 경로에서는 절대 호출하지 않는다 — 호출부는
 * {@code IdentifierCodeBackfillRunner}(기본 비활성, 배포 시 opt-in 실행)뿐이다.
 *
 * <p><b>이 백필이 끝나야만 다음 단계(NOT NULL + UNIQUE 제약)를 안전하게 걸 수 있다.</b> 4개 컬럼
 * 전부 NULL이 0건임을 확인하기 전에 엔티티를 nullable=false로 바꾸면, 기존 데이터가 있는 DB에서는
 * {@code ddl-auto: update}의 컬럼 제약 변경 자체가 실패할 수 있다 — 자세한 순서는
 * {@code docs/deploy-guide.md}의 "회원번호·상품코드·주문번호·공구팀 번호 배포 절차" 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentifierCodeBackfillService {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;

    /** 4개 테이블 전부 백필한다. 반환값은 실제로 새로 채운 행 수의 합계(로그/확인용). */
    public int backfillAll() {
        int memberCount = backfillMembers();
        int productCount = backfillProducts();
        int paymentCount = backfillPayments();
        int teamCount = backfillTeams();
        log.info("식별 코드 백필 전체 완료: memberCode={}, productCode={}, orderNo={}, teamNo={}",
                memberCount, productCount, paymentCount, teamCount);
        return memberCount + productCount + paymentCount + teamCount;
    }

    public int backfillMembers() {
        List<Long> ids = memberRepository.findIdsByMemberCodeIsNull();
        ids.forEach(this::backfillMemberOne);
        log.info("member_code 백필 완료: 대상행수={}", ids.size());
        return ids.size();
    }

    @Transactional
    public void backfillMemberOne(Long id) {
        memberRepository.findById(id).ifPresent(member -> {
            if (member.getMemberCode() == null) {
                member.assignMemberCode(IdentifierCodeFormatter.memberCode(member.getId()));
                memberRepository.save(member);
            }
        });
    }

    public int backfillProducts() {
        List<Long> ids = productRepository.findIdsByProductCodeIsNull();
        ids.forEach(this::backfillProductOne);
        log.info("product_code 백필 완료: 대상행수={}", ids.size());
        return ids.size();
    }

    @Transactional
    public void backfillProductOne(Long id) {
        productRepository.findById(id).ifPresent(product -> {
            if (product.getProductCode() == null) {
                product.assignProductCode(IdentifierCodeFormatter.productCode(product.getId()));
                productRepository.save(product);
            }
        });
    }

    public int backfillPayments() {
        List<Long> ids = paymentRepository.findIdsByOrderNoIsNull();
        ids.forEach(this::backfillPaymentOne);
        log.info("order_no 백필 완료: 대상행수={}", ids.size());
        return ids.size();
    }

    @Transactional
    public void backfillPaymentOne(Long id) {
        paymentRepository.findById(id).ifPresent(payment -> {
            if (payment.getOrderNo() == null) {
                payment.assignOrderNo(IdentifierCodeFormatter.orderNo(payment.getId(), payment.getPaidAt()));
                paymentRepository.save(payment);
            }
        });
    }

    public int backfillTeams() {
        List<Long> ids = groupBuyTeamRepository.findIdsByTeamNoIsNull();
        ids.forEach(this::backfillTeamOne);
        log.info("team_no 백필 완료: 대상행수={}", ids.size());
        return ids.size();
    }

    @Transactional
    public void backfillTeamOne(Long id) {
        groupBuyTeamRepository.findById(id).ifPresent(team -> {
            if (team.getTeamNo() == null) {
                team.assignTeamNo(IdentifierCodeFormatter.teamNo(team.getId()));
                groupBuyTeamRepository.save(team);
            }
        });
    }
}
