package com.futsal.tournament.controller;

import com.futsal.tournament.dto.ApplicationCreateRequest;
import com.futsal.tournament.dto.ApplicationProcessRequest;
import com.futsal.tournament.dto.ApplicationResponse;
import com.futsal.tournament.service.TournamentApplicationService;
import com.futsal.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 대회 신청 API 컨트롤러
 */
@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentApplicationController {

    private final TournamentApplicationService applicationService;

    /**
     * 대회 신청
     * POST /api/tournaments/{tournamentId}/applications
     */
    @PostMapping("/{tournamentId}/applications")
    public ResponseEntity<ApplicationResponse> createApplication(
            @PathVariable Long tournamentId,
            @Valid @RequestBody ApplicationCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ApplicationResponse response = applicationService.createApplication(tournamentId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 대회별 신청서 목록 조회 (주최자용)
     * GET /api/tournaments/{tournamentId}/applications
     */
    @GetMapping("/{tournamentId}/applications")
    public ResponseEntity<List<ApplicationResponse>> getApplicationsByTournament(
            @PathVariable Long tournamentId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ApplicationResponse> applications = 
                applicationService.getApplicationsByTournament(tournamentId, user);
        return ResponseEntity.ok(applications);
    }

    /**
     * 팀별 신청서 목록 조회
     * GET /api/teams/{teamId}/applications
     */
    @GetMapping("/teams/{teamId}/applications")
    public ResponseEntity<List<ApplicationResponse>> getApplicationsByTeam(
            @PathVariable Long teamId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ApplicationResponse> applications = 
                applicationService.getApplicationsByTeam(teamId, user);
        return ResponseEntity.ok(applications);
    }

    /**
     * 신청 승인
     * POST /api/tournaments/applications/{applicationId}/approve
     */
    @PostMapping("/applications/{applicationId}/approve")
    public ResponseEntity<ApplicationResponse> approveApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ApplicationResponse response = applicationService.approveApplication(applicationId, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 신청 거부
     * POST /api/tournaments/applications/{applicationId}/reject
     */
    @PostMapping("/applications/{applicationId}/reject")
    public ResponseEntity<ApplicationResponse> rejectApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationProcessRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ApplicationResponse response = applicationService.rejectApplication(applicationId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 신청 취소
     * POST /api/tournaments/applications/{applicationId}/cancel
     */
    @PostMapping("/applications/{applicationId}/cancel")
    public ResponseEntity<ApplicationResponse> cancelApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ApplicationResponse response = applicationService.cancelApplication(applicationId, user);
        return ResponseEntity.ok(response);
    }
}
