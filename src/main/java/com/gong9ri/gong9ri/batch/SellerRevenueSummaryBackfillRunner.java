package com.gong9ri.gong9ri.batch;

import com.gong9ri.gong9ri.service.SellerRevenueSummaryBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * seller_revenue_summary 1회성 백필 실행기(docs/db/seller_revenue_summary.md).
 * 조회마다 실행되는 지연 부트스트랩과 달리, 배포 시점에 딱 한 번 켜서 실행하고 다시 꺼두는 용도다.
 *
 * <p>기본은 꺼져 있다({@code app.backfill.seller-revenue-summary=true}일 때만 이 빈이 등록되고
 * 애플리케이션 기동 시 실행된다) — {@code application.yaml}에 아무 설정도 없으면
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}가 매치되지 않아
 * 이 컴포넌트 자체가 등록되지 않는다.
 *
 * <p>실행 절차: 1) 배포 환경변수/커맨드라인 인자로 {@code app.backfill.seller-revenue-summary=true}를
 * 한 번 설정 2) 애플리케이션을 기동한다(기동 시 1회 실행, 결과는 로그로 확인) 3) 완료 후 그 설정을
 * 다시 제거하거나 {@code false}로 되돌린다(재기동마다 재실행되지 않도록).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backfill.seller-revenue-summary", havingValue = "true")
public class SellerRevenueSummaryBackfillRunner implements ApplicationRunner {

    private final SellerRevenueSummaryBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("seller_revenue_summary 백필 실행기 시작");
        int createdCount = backfillService.backfillMissingSummaries();
        log.info("seller_revenue_summary 백필 실행기 종료: createdCount={}", createdCount);
    }
}
