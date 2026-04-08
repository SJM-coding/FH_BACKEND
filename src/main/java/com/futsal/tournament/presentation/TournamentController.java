package com.futsal.tournament.presentation;

import com.futsal.tournament.presentation.dto.TournamentCreateRequest;
import com.futsal.tournament.presentation.dto.TournamentPageResponse;
import com.futsal.tournament.presentation.dto.TournamentResponse;
import com.futsal.tournament.presentation.dto.TournamentListResponse;
import com.futsal.tournament.presentation.dto.TournamentResultRequest;
import com.futsal.tournament.presentation.dto.TournamentResultResponse;
import com.futsal.tournament.presentation.dto.TournamentUpdateRequest;
import com.futsal.user.domain.User;
import com.futsal.common.storage.S3Service;
import com.futsal.tournament.application.TournamentResultService;
import com.futsal.tournament.application.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;
    private final TournamentResultService resultService;
    private final S3Service s3Service;

    /**
     * 단일 포스터 업로드
     * POST /api/tournaments/poster
     */
    @PostMapping("/poster")
    public ResponseEntity<String> uploadPoster(@RequestParam("file") MultipartFile file) {
        String posterUrl = s3Service.uploadPoster(file);
        return ResponseEntity.ok(posterUrl);
    }

    /**
     * 여러 포스터 업로드
     * POST /api/tournaments/posters
     */
    @PostMapping("/posters")
    public ResponseEntity<List<String>> uploadPosters(@RequestParam("files") List<MultipartFile> files) {
        List<String> posterUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            String posterUrl = s3Service.uploadPoster(file);
            posterUrls.add(posterUrl);
        }
        return ResponseEntity.ok(posterUrls);
    }

    /**
     * Phase 2-3: 내가 등록한 대회 목록
     * GET /api/tournaments/my
     * (id 경로 변수보다 먼저 매핑되어야 함)
     */
    @GetMapping("/my")
    public ResponseEntity<List<TournamentListResponse>> getMyTournaments(
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<TournamentListResponse> tournaments = tournamentService.getMyTournaments(user);
        return ResponseEntity.ok(tournaments);
    }

    /**
     * 키워드 검색 페이지네이션
     * GET /api/tournaments/search?keyword={keyword}&page=0&size=12
     */
    @GetMapping("/search")
    public ResponseEntity<TournamentPageResponse> searchTournaments(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        TournamentPageResponse response = tournamentService.searchTournaments(keyword, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 대회 등록 (로그인 필요)
     * POST /api/tournaments
     */
    @PostMapping
    public ResponseEntity<TournamentResponse> createTournament(
            @Valid @RequestBody TournamentCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        TournamentResponse response = tournamentService.createTournament(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 대회 목록 페이지네이션 조회
     * GET /api/tournaments?page=0&size=12&gender=MALE&playerType=NON_PRO&recruitmentStatus=OPEN
     */
    @GetMapping
    public ResponseEntity<TournamentPageResponse> getAllTournaments(
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String playerType,
            @RequestParam(required = false) String recruitmentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        TournamentPageResponse response = tournamentService.getTournaments(
            gender, playerType, recruitmentStatus, page, size
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Phase 2-4: 단일 대회 조회 (조회수 증가)
     * GET /api/tournaments/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponse> getTournamentById(@PathVariable Long id) {
        TournamentResponse tournament = tournamentService.getTournamentById(id);
        return ResponseEntity.ok(tournament);
    }

    /**
     * Phase 2-2: 대회 수정 (등록자만 가능)
     * PUT /api/tournaments/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<TournamentResponse> updateTournament(
            @PathVariable Long id,
            @Valid @RequestBody TournamentUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        TournamentResponse response = tournamentService.updateTournament(id, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 대회 삭제 (등록자만 가능)
     * DELETE /api/tournaments/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTournament(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        tournamentService.deleteTournament(id, user);
        return ResponseEntity.noContent().build();
    }

    // ============================================
    // 대회 결과 (순위) 관리
    // ============================================

    /**
     * 대회 결과 입력 (개최자만 가능)
     * 자동으로 팀 수상 경력 생성
     * POST /api/tournaments/{id}/results
     */
    @PostMapping("/{id}/results")
    public ResponseEntity<List<TournamentResultResponse>> recordResults(
            @PathVariable Long id,
            @Valid @RequestBody TournamentResultRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<TournamentResultResponse> results = resultService.recordResults(id, request, user);
        return ResponseEntity.ok(results);
    }

    /**
     * 대회 결과 조회
     * GET /api/tournaments/{id}/results
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<List<TournamentResultResponse>> getResults(@PathVariable Long id) {
        List<TournamentResultResponse> results = resultService.getResults(id);
        return ResponseEntity.ok(results);
    }

    /**
     * 대회 결과 삭제 (개최자만 가능)
     * DELETE /api/tournaments/{id}/results
     */
    @DeleteMapping("/{id}/results")
    public ResponseEntity<Void> deleteResults(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        resultService.deleteResults(id, user);
        return ResponseEntity.noContent().build();
    }
}
