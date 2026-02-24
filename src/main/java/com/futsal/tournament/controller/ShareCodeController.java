package com.futsal.tournament.controller;

import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.dto.BracketResponse;
import com.futsal.tournament.dto.MatchResponse;
import com.futsal.tournament.dto.MatchResultRequest;
import com.futsal.tournament.repository.TournamentRepository;
import com.futsal.tournament.service.BracketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ShareCode 기반 대진표 접근 API
 * 인증 없이 shareCode만으로 대진표 조회 및 경기 결과 입력 가능
 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareCodeController {

    private final TournamentRepository tournamentRepository;
    private final BracketService bracketService;

    /**
     * ShareCode로 대회 정보 조회
     * GET /api/share/{shareCode}
     */
    @GetMapping("/{shareCode}")
    public ResponseEntity<?> getTournamentInfo(@PathVariable String shareCode) {
        Tournament tournament = tournamentRepository.findByShareCode(shareCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 공유코드입니다."));
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
     * ShareCode로 대진표 조회
     * GET /api/share/{shareCode}/bracket
     */
    @GetMapping("/{shareCode}/bracket")
    public ResponseEntity<?> getBracket(@PathVariable String shareCode) {
        Tournament tournament = tournamentRepository.findByShareCode(shareCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 공유코드입니다."));
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            return ResponseEntity.badRequest().body(Map.of("error", "아직 대진표가 생성되지 않았습니다."));
        }

        try {
            BracketResponse bracket = bracketService.getBracket(tournament.getId());
            return ResponseEntity.ok(bracket);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ShareCode로 경기 결과 입력
     * POST /api/share/{shareCode}/matches/{matchId}/result
     */
    @PostMapping("/{shareCode}/matches/{matchId}/result")
    public ResponseEntity<?> recordMatchResult(
            @PathVariable String shareCode,
            @PathVariable Long matchId,
            @RequestBody MatchResultRequest request
    ) {
        Tournament tournament = tournamentRepository.findByShareCode(shareCode)
                .orElse(null);

        if (tournament == null) {
            return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 공유코드입니다."));
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            return ResponseEntity.badRequest().body(Map.of("error", "아직 대진표가 생성되지 않았습니다."));
        }

        try {
            MatchResponse result = bracketService.recordMatchResult(tournament.getId(), matchId, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
