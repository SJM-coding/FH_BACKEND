package com.futsal.tournament.controller;

import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.dto.ParticipantResponse;
import com.futsal.tournament.dto.JoinTournamentRequest;
import com.futsal.tournament.dto.TournamentResponse;
import com.futsal.tournament.service.ParticipantService;
import com.futsal.tournament.service.TournamentService;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 대회 참가 API 컨트롤러
 */
@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService participantService;
    private final TournamentService tournamentService;

    /**
     * 공유코드 생성
     * POST /api/tournaments/{id}/share-code
     */
    @PostMapping("/{tournamentId}/share-code")
    public ResponseEntity<?> generateShareCode(
            @PathVariable Long tournamentId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String shareCode = participantService.generateShareCode(tournamentId, user.getId());
            return ResponseEntity.ok(Map.of("shareCode", shareCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 공유코드로 대회 조회
     * GET /api/tournaments/by-code/{shareCode}
     */
    @GetMapping("/by-code/{shareCode}")
    public ResponseEntity<?> getTournamentByCode(@PathVariable String shareCode) {
        try {
            TournamentResponse tournament = tournamentService.getTournamentByShareCode(shareCode);
            return ResponseEntity.ok(tournament);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 대회 참가 신청
     * POST /api/tournaments/join
     */
    @PostMapping("/join")
    public ResponseEntity<?> joinTournament(
            @RequestBody JoinTournamentRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TournamentParticipant participant = participantService.joinTournament(
                    request.getShareCode(),
                    request.getTeamId(),
                    user.getId()
            );
            return ResponseEntity.ok(ParticipantResponse.from(participant));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 참가 취소
     * DELETE /api/tournaments/{tournamentId}/participants/{teamId}
     */
    @DeleteMapping("/{tournamentId}/participants/{teamId}")
    public ResponseEntity<?> withdrawParticipant(
            @PathVariable Long tournamentId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            participantService.withdrawParticipant(tournamentId, teamId, user.getId());
            return ResponseEntity.ok(Map.of("message", "참가가 취소되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 참가팀 목록 조회
     * GET /api/tournaments/{tournamentId}/participants
     */
    @GetMapping("/{tournamentId}/participants")
    public ResponseEntity<List<ParticipantResponse>> getParticipants(
            @PathVariable Long tournamentId
    ) {
        List<TournamentParticipant> participants = participantService.getParticipants(tournamentId);
        List<ParticipantResponse> responses = participants.stream()
                .map(ParticipantResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
