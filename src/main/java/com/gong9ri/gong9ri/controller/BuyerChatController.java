package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ChatMessageRequest;
import com.gong9ri.gong9ri.dto.ChatMessageResponse;
import com.gong9ri.gong9ri.dto.ChatSessionUsageResponse;
import com.gong9ri.gong9ri.dto.ChatStatsResponse;
import com.gong9ri.gong9ri.service.BuyerChatService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BuyerChatController {

    private final BuyerChatService buyerChatService;

    @PostMapping(value = "/buyer/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @AuthenticationPrincipal MemberUserDetails principal,
            @Valid @RequestBody ChatMessageRequest request) {
        return buyerChatService.streamChat(principal, request);
    }

    @GetMapping("/buyer/chat/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> history(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(buyerChatService.history(principal, sessionId)));
    }

    @GetMapping("/buyer/chat/sessions/{sessionId}/usage")
    public ResponseEntity<ApiResponse<ChatSessionUsageResponse>> sessionUsage(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(buyerChatService.sessionUsage(principal, sessionId)));
    }

    @GetMapping("/chat/stats")
    public ResponseEntity<ApiResponse<List<ChatStatsResponse>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(buyerChatService.stats()));
    }
}
