package com.futsal.tournament.presentation;

import com.futsal.tournament.application.ScheduleImageParseService;
import com.futsal.tournament.presentation.dto.ScheduleParseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tournaments/{tournamentId}/schedule-image")
@RequiredArgsConstructor
public class ScheduleImageParseController {

  private final ScheduleImageParseService scheduleImageParseService;

  /**
   * 운영자가 업로드 페이지 진입 시 호출 — Modal 컨테이너 pre-warm.
   * GET /api/tournaments/{tournamentId}/schedule-image/warmup
   */
  @GetMapping("/warmup")
  public ResponseEntity<Void> warmup() {
    // AI 파싱 기능 준비 완료 전까지 비활성화
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  /**
   * 일정표/대진표 이미지를 AI로 분석해 경기 정보 미리보기를 반환한다.
   * 운영자가 결과를 확인 후 저장을 확정한다.
   *
   * POST /api/tournaments/{tournamentId}/schedule-image/parse
   */
  @PostMapping("/parse")
  public ResponseEntity<ScheduleParseResponse> parse(
      @PathVariable Long tournamentId,
      @RequestParam("image") MultipartFile image
  ) {
    ScheduleParseResponse response =
        scheduleImageParseService.parse(tournamentId, image);
    return ResponseEntity.ok(response);
  }
}
