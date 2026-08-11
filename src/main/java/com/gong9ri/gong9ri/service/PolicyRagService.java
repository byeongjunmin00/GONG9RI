package com.gong9ri.gong9ri.service;

import java.util.List;

/**
 * 정책 문서 RAG 검색(docs/dev/ongoing/ai-policy-rag.md). 구매자 챗봇({@link BuyerChatService})이 정책
 * 관련 질문("환불은 언제 되나요?" 등)에 답할 근거 문맥을 가져오는 용도로, 나중에 이 인터페이스를 생성자
 * 주입받아 쓸 수 있게 인터페이스로 분리했다(챗봇 본체 결합은 이번 스코프 밖 — 검색까지만 담당).
 *
 * <p>입력은 구매자의 자연어 질의 원문, 출력은 유사도 상위 K개 정책 스니펫 텍스트 목록이다.
 *
 * <p><b>계약: 반환된 스니펫이 항상 질문과 관련 있다고 가정하면 안 된다.</b> 이 구현은 유사도 threshold로
 * "관련 없으면 걸러내기"를 하지 않는다 — 이 코퍼스 규모(정책 문서 2개, 6청크)에서는 threshold로 관련/무관을
 * 안정적으로 가를 수 없음을 실측으로 확인했다(설계 변경 경위: {@code docs/dev/ongoing/ai-policy-rag.md}
 * "설계 변경" 섹션, 실측 근거: {@code docs/logs/ai/policy-rag/001-policy-rag.md} Attempt 1~2). 그래서
 * 항상 상위 K개를 그대로 반환하며, **호출하는 쪽이 반환된 문맥의 관련성을 최종 판단해야 한다**(예:
 * {@code BuyerChatService}의 시스템 프롬프트에서 "제공된 문맥이 질문과 무관하면 참고하지 말고 무시하라"고
 * LLM에 지시하는 방식). 챗봇 쪽에서는 이 목록을 그대로 시스템 프롬프트 뒤에 덧붙이거나 필요에 맞게 가공해서
 * 쓰면 된다(RAG 조립 방식은 챗봇 담당자가 결정 — 여기서는 검색 결과만 제공).
 */
public interface PolicyRagService {

    List<String> findRelevantSnippets(String query);
}
