package com.gong9ri.gong9ri.config;

import com.gong9ri.gong9ri.entity.PromptCategory;
import com.gong9ri.gong9ri.entity.PromptTemplate;
import com.gong9ri.gong9ri.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 판매자 상품등록 AI 도우미의 프롬프트 템플릿 초기 시드 — 기동마다 두 카테고리(FOOD/GENERAL) 행이 있는지
 * 확인하고 없으면만 만든다(있으면 절대 덮어쓰지 않음 — DB에서 직접 수정한 내용을 기동할 때마다
 * 지워버리면 "재배포 없이 수정 가능" 요구사항이 무의미해지기 때문, docs/dev/ai/product-suggestion/design.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTemplateSeeder implements CommandLineRunner {

    private final PromptTemplateRepository promptTemplateRepository;

    private static final String FOOD_TEMPLATE = """
            너는 공동구매 플랫폼 GONG9RI의 상품 등록을 돕는 도우미다. 판매자가 신선식품을 대충 설명하면,
            아래 정보를 바탕으로 상품 등록에 쓸 값을 제안해라.

            - 상품명(suggestedName): 신선함과 원산지·중량이 드러나게 간결하게(20자 이내)
            - 상품 설명(suggestedDescription): 유통기한, 보관방법, 중량/수량을 반드시 포함해서 2~3문장으로
            - 기본가(suggestedBasePrice): 판매자가 언급한 가격이 있으면 그걸 원 단위 정수로, 없으면 합리적으로 추정.
              한글 단위(만/천)로 쓴 금액은 자릿수를 나눠서 정확히 환산해라. 만=10000, 천=1000이므로
              "이만오천원"은 2*10000+5*1000=25000, "만오천원"은 10000+5000=15000이다.
              절대 앞자리 숫자만 보고 넘겨짚지 마라.
            - 최대인원(suggestedMaxParticipants): 신선식품은 소량 다품종이 많아 5~15명 사이로 추정(별다른 언급 없으면 10)

            판매자 입력: {input}
            """;

    private static final String GENERAL_TEMPLATE = """
            너는 공동구매 플랫폼 GONG9RI의 상품 등록을 돕는 도우미다. 판매자가 일반 상품(신선식품 제외)을
            대충 설명하면, 아래 정보를 바탕으로 상품 등록에 쓸 값을 제안해라.

            - 상품명(suggestedName): 스펙(소재·사이즈 등)이 드러나게 간결하게(20자 이내)
            - 상품 설명(suggestedDescription): 소재, 사이즈, 사용 용도를 반드시 포함해서 2~3문장으로
            - 기본가(suggestedBasePrice): 판매자가 언급한 가격이 있으면 그걸 원 단위 정수로, 없으면 합리적으로 추정.
              한글 단위(만/천)로 쓴 금액은 자릿수를 나눠서 정확히 환산해라. 만=10000, 천=1000이므로
              "이만오천원"은 2*10000+5*1000=25000, "만오천원"은 10000+5000=15000이다.
              절대 앞자리 숫자만 보고 넘겨짚지 마라.
            - 최대인원(suggestedMaxParticipants): 일반 상품은 재고 여유가 있는 편이라 10~30명 사이로 추정(별다른 언급 없으면 20)

            판매자 입력: {input}
            """;

    @Override
    public void run(String... args) {
        seedIfMissing(PromptCategory.FOOD, FOOD_TEMPLATE);
        seedIfMissing(PromptCategory.GENERAL, GENERAL_TEMPLATE);
    }

    private void seedIfMissing(PromptCategory category, String content) {
        if (promptTemplateRepository.existsByCategory(category)) {
            return;
        }
        promptTemplateRepository.save(new PromptTemplate(category, content, 1));
        log.info("프롬프트 템플릿 시드 생성: category={}", category);
    }
}
