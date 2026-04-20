package com.futsal.tournament.presentation;

import com.futsal.tournament.application.BracketImageParseService;
import com.futsal.tournament.presentation.dto.GroupAssignmentDto;
import com.futsal.tournament.presentation.dto.GroupParseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/bracket-image")
@RequiredArgsConstructor
public class BracketImageParseController {

  private final BracketImageParseService bracketImageParseService;

  /**
   * 운영자가 업로드 페이지 진입 시 호출 — Modal 컨테이너 pre-warm.
   * GET /api/tournaments/{tournamentId}/bracket-image/warmup
   */
  @GetMapping("/warmup")
  public ResponseEntity<Void> warmup() {
    // AI 파싱 기능 준비 완료 전까지 비활성화
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  /**
   * 대진표 이미지를 AI로 분석해 팀 매칭 미리보기를 반환한다.
   * POST /api/tournaments/{tournamentId}/bracket-image/parse
   */
  /**
   * 대진표 이미지를 AI로 분석해 조별 팀 배정 미리보기를 반환한다.
   * POST /api/tournaments/{tournamentId}/bracket-image/parse
   */
  @PostMapping("/parse")
  public ResponseEntity<GroupParseResponse> parse(
      @PathVariable Long tournamentId,
      @RequestParam("image") MultipartFile image
  ) {
    return ResponseEntity.ok(
        bracketImageParseService.parse(tournamentId, image)
    );
  }

  /**
   * 운영자가 확인한 조별 팀 배정을 저장하고 경기를 생성한다.
   * POST /api/tournaments/{tournamentId}/bracket-image/confirm
   */
  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(
      @PathVariable Long tournamentId,
      @RequestBody List<GroupAssignmentDto> assignments
  ) {
    bracketImageParseService.confirm(tournamentId, assignments);
    return ResponseEntity.ok().build();
  }
}
