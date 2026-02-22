package com.futsal.tournament.controller;

import com.futsal.tournament.dto.*;
import com.futsal.tournament.service.BracketGeneratorService;
import com.futsal.tournament.service.BracketService;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 대진표 API 컨트롤러
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/bracket")
@RequiredArgsConstructor
public class BracketController {

    private final BracketGeneratorService generatorService;
    private final BracketService bracketService;

    /**
     * 대진표 생성
     * POST /api/tournaments/{tournamentId}/bracket/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateBracket(
            @PathVariable Long tournamentId,
            @RequestBody BracketGenerateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            generatorService.generateBracket(tournamentId, request.getParticipatingTeamIds());
            return ResponseEntity.ok().body(Map.of("message", "대진표가 생성되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 대진표 조회
     * GET /api/tournaments/{tournamentId}/bracket
     */
    @GetMapping
    public ResponseEntity<BracketResponse> getBracket(@PathVariable Long tournamentId) {
        try {
            BracketResponse bracket = bracketService.getBracket(tournamentId);
            return ResponseEntity.ok(bracket);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 경기 결과 입력
     * POST /api/tournaments/{tournamentId}/bracket/matches/{matchId}/result
     */
    @PostMapping("/matches/{matchId}/result")
    public ResponseEntity<?> recordMatchResult(
            @PathVariable Long tournamentId,
            @PathVariable Long matchId,
            @RequestBody MatchResultRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MatchResponse result = bracketService.recordMatchResult(tournamentId, matchId, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
