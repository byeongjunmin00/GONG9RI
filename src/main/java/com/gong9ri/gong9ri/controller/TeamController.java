package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.TeamCreateRequest;
import com.gong9ri.gong9ri.dto.TeamJoinResponse;
import com.gong9ri.gong9ri.dto.TeamParticipantResponse;
import com.gong9ri.gong9ri.dto.TeamResponse;
import com.gong9ri.gong9ri.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/api/products/{productId}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(teamService.list(productId)));
    }

    @PostMapping("/api/products/{productId}/teams")
    public ResponseEntity<ApiResponse<TeamResponse>> create(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId,
            @Valid @RequestBody TeamCreateRequest request) {
        TeamResponse response = teamService.create(principal, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/api/teams/{teamId}/join")
    public ResponseEntity<ApiResponse<TeamJoinResponse>> join(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long teamId) {
        return ResponseEntity.ok(ApiResponse.success(teamService.join(principal, teamId)));
    }

    @GetMapping("/api/teams/{teamId}/participants")
    public ResponseEntity<ApiResponse<List<TeamParticipantResponse>>> participants(@PathVariable Long teamId) {
        return ResponseEntity.ok(ApiResponse.success(teamService.participants(teamId)));
    }
}
