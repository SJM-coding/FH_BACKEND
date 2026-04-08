package com.futsal.tournament.presentation;

import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.presentation.dto.BatchMatchScheduleRequest;
import com.futsal.tournament.presentation.dto.BracketResponse;
import com.futsal.tournament.presentation.dto.MatchResponse;
import com.futsal.tournament.presentation.dto.MatchResultRequest;
import com.futsal.tournament.presentation.dto.QualifierSelectionRequest;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.application.BracketCommandService;
import com.futsal.tournament.application.BracketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 운영진 코드(StaffCode) 기반 대진표 접근 API
 * 인증 없이 운영진 코드만으로 대진표 조회 및 경기 결과 입력 가능
 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareCodeController {

    private final TournamentRepository tournamentRepository;
    private final BracketQueryService bracketQueryService;
    private final BracketCommandService bracketCommandService;

    /**
     * 운영진 코드로 대회 정보 조회
     * GET /api/share/{staffCode}
     */
    @GetMapping("/{staffCode}")
    public ResponseEntity<?> getTournamentInfo(@PathVariable String staffCode) {
        Tournament tournament = tournamentRepository.findByStaffCode(staffCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 운영진코드입니다."));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("tournamentId", tournament.getId());
        response.put("title", tournament.getTitle());
        response.put("tournamentDate", tournament.getTournamentDate());
        response.put("location", tournament.getLocation());
        response.put("tournamentType", tournament.getTournamentType().name());
        response.put("bracketGenerated", tournament.getBracketGenerated());

        return ResponseEntity.ok(response);
    }

    /**
     * 운영진 코드로 대진표 조회
     * GET /api/share/{staffCode}/bracket
     */
    @GetMapping("/{staffCode}/bracket")
    public ResponseEntity<?> getBracket(@PathVariable String staffCode) {
        Tournament tournament = tournamentRepository.findByStaffCode(staffCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 운영진코드입니다."));
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            return ResponseEntity.badRequest().body(Map.of("error", "아직 대진표가 생성되지 않았습니다."));
        }

        try {
            BracketResponse bracket = bracketQueryService.getBracket(tournament.getId());
            return ResponseEntity.ok(bracket);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 운영진 코드로 경기 결과 입력
     * POST /api/share/{staffCode}/matches/{matchId}/result
     */
    @PostMapping("/{staffCode}/matches/{matchId}/result")
    public ResponseEntity<?> recordMatchResult(
            @PathVariable String staffCode,
            @PathVariable Long matchId,
            @RequestBody MatchResultRequest request
    ) {
        Tournament tournament = tournamentRepository.findByStaffCode(staffCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 운영진코드입니다."));
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            return ResponseEntity.badRequest().body(Map.of("error", "아직 대진표가 생성되지 않았습니다."));
        }

        try {
            MatchResponse result = bracketCommandService.recordMatchResult(tournament.getId(), matchId, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{staffCode}/select-qualifiers")
    public ResponseEntity<?> selectQualifiersAndGenerateKnockout(
            @PathVariable String staffCode,
            @RequestBody QualifierSelectionRequest request
    ) {
        Tournament tournament = tournamentRepository.findByStaffCode(staffCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 운영진코드입니다."));
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            return ResponseEntity.badRequest().body(Map.of("error", "아직 대진표가 생성되지 않았습니다."));
        }

        try {
            BracketResponse result = bracketCommandService.selectQualifiersAndGenerateKnockoutByShareCode(
                    tournament.getId(), request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 운영진 코드로 경기 일정 일괄 설정
     * PUT /api/share/{staffCode}/matches/schedules
     */
    @PutMapping("/{staffCode}/matches/schedules")
    public ResponseEntity<?> updateMatchSchedules(
            @PathVariable String staffCode,
            @RequestBody BatchMatchScheduleRequest request
    ) {
        Tournament tournament = tournamentRepository.findByStaffCode(staffCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 운영진코드입니다."));
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            return ResponseEntity.badRequest().body(Map.of("error", "아직 대진표가 생성되지 않았습니다."));
        }

        try {
            var results = bracketCommandService.updateMatchSchedules(tournament.getId(), request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
