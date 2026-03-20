package com.futsal.tournament.controller;

import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.dto.ParticipantResponse;
import com.futsal.tournament.dto.JoinTournamentRequest;
import com.futsal.tournament.dto.TournamentListResponse;
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
     * 운영진 코드 생성 (대회 확정 시)
     * POST /api/tournaments/{id}/staff-code
     */
    @PostMapping("/{tournamentId}/staff-code")
    public ResponseEntity<?> generateStaffCode(
            @PathVariable Long tournamentId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String staffCode = participantService.generateStaffCode(tournamentId, user.getId());
            return ResponseEntity.ok(Map.of("staffCode", staffCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 참가 코드로 대회 조회 (참가 신청용)
     * GET /api/tournaments/by-code/{participantCode}
     */
    @GetMapping("/by-code/{participantCode}")
    public ResponseEntity<?> getTournamentByParticipantCode(@PathVariable String participantCode) {
        try {
            TournamentResponse tournament = tournamentService.getTournamentByParticipantCode(participantCode);
            return ResponseEntity.ok(tournament);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 운영진 코드로 대회 조회 (점수 입력용)
     * GET /api/tournaments/by-staff-code/{staffCode}
     */
    @GetMapping("/by-staff-code/{staffCode}")
    public ResponseEntity<?> getTournamentByStaffCode(@PathVariable String staffCode) {
        try {
            TournamentResponse tournament = tournamentService.getTournamentByStaffCode(staffCode);
            return ResponseEntity.ok(tournament);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 대회 참가 신청 (참가 코드 사용)
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
            // shareCode 필드를 참가 코드로 사용 (프론트엔드 호환성 유지)
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

    /**
     * 내가 참가한 대회 목록 조회
     * GET /api/tournaments/participated
     */
    @GetMapping("/participated")
    public ResponseEntity<?> getMyParticipatedTournaments(
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Tournament> tournaments = participantService.getMyParticipatedTournaments(user.getId());
        List<TournamentListResponse> responses = tournaments.stream()
                .map(t -> new TournamentListResponse(
                        t.getId(),
                        t.getTitle(),
                        t.getTournamentDate(),
                        t.getLocation(),
                        t.getRecruitmentStatus(),
                        t.getPosterUrls() != null && !t.getPosterUrls().isEmpty() ? t.getPosterUrls().get(0) : null,
                        t.getRegisteredBy() != null ? t.getRegisteredBy().getNickname() : null,
                        t.getRegisteredBy() != null ? t.getRegisteredBy().getProfileImageUrl() : null,
                        t.getGender(),
                        t.getPlayerType(),
                        t.getIsExternal()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
