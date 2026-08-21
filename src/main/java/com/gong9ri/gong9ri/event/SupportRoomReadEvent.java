package com.gong9ri.gong9ri.event;

/**
 * 상담방을 한쪽이 읽었다는 신호 (support/chat 읽음 표시, 2026-08-21).
 *
 * <p>{@code readByAdmin}은 <b>읽은 사람이 관리자인지</b>를 뜻한다 — 받는 쪽은 "내가 보낸 메시지가
 * 읽혔는지"를 판단해야 하므로, 자기가 보낸 신호는 무시하고 반대쪽이 읽었을 때만 표시를 갱신한다.
 */
public record SupportRoomReadEvent(Long roomId, boolean readByAdmin) {

    /** 메시지·입력중 신호와 구분되도록 type을 실어 보낸다 — 프론트가 이걸로 갈라 처리한다. */
    @SuppressWarnings("unused")
    public String getType() {
        return "READ";
    }
}
