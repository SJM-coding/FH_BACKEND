package com.futsal.tournament.application;

import com.futsal.shared.infrastructure.S3Service;
import com.futsal.tournament.domain.Bracket;
import com.futsal.tournament.domain.BracketType;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.presentation.dto.BracketResponse;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 대진표 이미지 관리 서비스
 * 수동 대진표 이미지 업로드/삭제 및 AUTO ↔ MANUAL 모드 전환 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketImageService {

  private final TournamentRepository tournamentRepository;
  private final TournamentMatchRepository matchRepository;
  private final BracketRepository bracketRepository;
  private final S3Service s3Service;

  /**
   * 대진표 이미지 업로드 (수동 대진표로 전환)
   */
  @Transactional
  public BracketResponse uploadBracketImages(
      Long tournamentId, List<MultipartFile> files, User user) {

    Tournament tournament = findTournament(tournamentId);
    verifyOwner(tournament, user);

    Bracket bracket = bracketRepository.findByTournamentId(tournamentId)
        .orElseGet(() -> Bracket.createDefault(tournamentId));

    // 기존 자동 생성 데이터 삭제
    if (!bracket.isManual() && bracket.isGenerated()) {
      log.info("기존 자동 생성 대진표 삭제: tournamentId={}", tournamentId);
      matchRepository.deleteByTournamentId(tournamentId);
    }

    List<String> imageUrls = new ArrayList<>();
    for (MultipartFile file : files) {
      imageUrls.add(s3Service.uploadBracketImage(file));
    }

    bracket.switchToManual(imageUrls);
    bracketRepository.save(bracket);

    log.info("대진표 이미지 업로드 완료: tournamentId={}, imageCount={}",
        tournamentId, imageUrls.size());

    return BracketResponse.builder()
        .tournamentId(tournament.getId())
        .tournamentTitle(tournament.getTitle())
        .tournamentType(tournament.getTournamentType() != null
            ? tournament.getTournamentType().name() : null)
        .bracketType(BracketType.MANUAL.name())
        .bracketGenerated(true)
        .bracketImageUrls(imageUrls)
        .build();
  }

  /**
   * 대진표 이미지 삭제 (자동 생성 모드로 전환)
   */
  @Transactional
  public void clearBracketImages(Long tournamentId, User user) {
    Tournament tournament = findTournament(tournamentId);
    verifyOwner(tournament, user);

    bracketRepository.findByTournamentId(tournamentId).ifPresent(bracket -> {
      bracket.switchToAuto();
      bracketRepository.save(bracket);
    });

    log.info("대진표 이미지 삭제 완료, AUTO 모드로 전환: tournamentId={}", tournamentId);
  }

  private Tournament findTournament(Long tournamentId) {
    return tournamentRepository.findById(tournamentId)
        .orElseThrow(() -> new RuntimeException(
            "대회를 찾을 수 없습니다: " + tournamentId));
  }

  private void verifyOwner(Tournament tournament, User user) {
    if (!tournament.isRegisteredBy(user.getId())) {
      throw new RuntimeException("대진표를 수정할 권한이 없습니다.");
    }
  }
}
