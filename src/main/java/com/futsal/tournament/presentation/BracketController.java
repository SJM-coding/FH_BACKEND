package com.futsal.tournament.presentation;

import com.futsal.tournament.presentation.dto.*;
import com.futsal.tournament.application.BracketCommandService;
import com.futsal.tournament.application.BracketGeneratorService;
import com.futsal.tournament.application.BracketImageService;
import com.futsal.tournament.application.BracketQueryService;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 대진표 API 컨트롤러
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/bracket")
@RequiredArgsConstructor
public class BracketController {

  private final BracketGeneratorService generatorService;
  private final BracketQueryService bracketQueryService;
  private final BracketCommandService bracketCommandService;
  private final BracketImageService bracketImageService;

  /**
   * 대진표 생성
   */
  @PostMapping("/generate")
  public ResponseEntity<?> generateBracket(
      @PathVariable Long tournamentId,
      @RequestBody BracketGenerateRequest request,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      generatorService.generateBracket(tournamentId, request);
      return ResponseEntity.ok(Map.of("message", "대진표가 생성되었습니다."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 대진표 조회
   */
  @GetMapping
  public ResponseEntity<?> getBracket(@PathVariable Long tournamentId) {
    try {
      return ResponseEntity.ok(bracketQueryService.getBracket(tournamentId));
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 경기 일정 단건 설정
   */
  @PutMapping("/matches/{matchId}/schedule")
  public ResponseEntity<?> updateMatchSchedule(
      @PathVariable Long tournamentId,
      @PathVariable Long matchId,
      @RequestBody MatchScheduleRequest request,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      MatchResponse result =
          bracketCommandService.updateMatchSchedule(tournamentId, matchId, request);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 경기 일정 일괄 설정
   */
  @PutMapping("/matches/schedules")
  public ResponseEntity<?> updateMatchSchedules(
      @PathVariable Long tournamentId,
      @RequestBody BatchMatchScheduleRequest request,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      List<MatchResponse> result =
          bracketCommandService.updateMatchSchedules(tournamentId, request);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 경기 결과 입력
   */
  @PostMapping("/matches/{matchId}/result")
  public ResponseEntity<?> recordMatchResult(
      @PathVariable Long tournamentId,
      @PathVariable Long matchId,
      @RequestBody MatchResultRequest request,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      MatchResponse result =
          bracketCommandService.recordMatchResult(tournamentId, matchId, request);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 대진표 이미지 업로드 (수동 대진표)
   */
  @PostMapping("/images")
  public ResponseEntity<?> uploadBracketImages(
      @PathVariable Long tournamentId,
      @RequestParam("files") List<MultipartFile> files,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    if (files == null || files.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "업로드할 파일이 없습니다."));
    }
    if (files.size() > 5) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "대진표 이미지는 최대 5장까지 업로드할 수 있습니다."));
    }
    try {
      BracketResponse result =
          bracketImageService.uploadBracketImages(tournamentId, files, user);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 대진표 이미지 삭제 (자동 생성 모드로 전환)
   */
  @DeleteMapping("/images")
  public ResponseEntity<?> clearBracketImages(
      @PathVariable Long tournamentId,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      bracketImageService.clearBracketImages(tournamentId, user);
      return ResponseEntity.ok(Map.of("message", "대진표 이미지가 삭제되었습니다."));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 조별리그 진출팀 수동 선택 후 결선 토너먼트 생성
   */
  @PostMapping("/select-qualifiers")
  public ResponseEntity<?> selectQualifiersAndGenerateKnockout(
      @PathVariable Long tournamentId,
      @RequestBody QualifierSelectionRequest request,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      BracketResponse result =
          bracketCommandService.selectQualifiersAndGenerateKnockout(
              tournamentId, request, user);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * 결선 1라운드 팀 수동 재배치
   */
  @PutMapping("/knockout/assignments")
  public ResponseEntity<?> updateKnockoutAssignments(
      @PathVariable Long tournamentId,
      @RequestBody KnockoutMatchAssignmentRequest request,
      @AuthenticationPrincipal User user
  ) {
    if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    try {
      BracketResponse result =
          bracketCommandService.updateKnockoutAssignments(
              tournamentId, request, user);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}
