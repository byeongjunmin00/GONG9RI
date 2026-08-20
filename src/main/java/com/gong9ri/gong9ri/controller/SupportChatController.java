package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.SupportRoomResponse;
import com.gong9ri.gong9ri.service.SupportChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 쪽 상담 API (support/chat). 실시간 송수신은 WebSocket이고, 여기는 방을 열고 지난 대화를
 * 불러오는 용도다 — 접속 전에 쌓인 메시지를 봐야 하므로 REST 조회가 반드시 필요하다.
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportChatController {

    private final SupportChatService supportChatService;

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<SupportRoomResponse>> openRoom(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(supportChatService.openRoom(principal.getMember())));
    }

    /** 내 열린 상담. 없으면 data가 null이다 — 조회가 방을 만드는 부작용을 갖지 않게 분리했다. */
    @GetMapping("/rooms/me")
    public ResponseEntity<ApiResponse<SupportRoomResponse>> myRoom(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(supportChatService.myRoom(principal.getMember())));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<SupportRoomResponse>> room(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long roomId) {
        return ResponseEntity.ok(ApiResponse.success(supportChatService.room(principal.getMember(), roomId)));
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long roomId) {
        supportChatService.markRead(principal.getMember(), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/close")
    public ResponseEntity<Void> close(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long roomId) {
        supportChatService.close(principal.getMember(), roomId);
        return ResponseEntity.noContent().build();
    }
}
