package com.futsal.tournament.application;

import com.futsal.team.domain.Team;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.presentation.dto.BracketResponse;
import com.futsal.tournament.presentation.dto.SplitBracketRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상위/하위 분리 토너먼트 생성 서비스
 *
 * 조별리그 완료 후 운영진이 팀 배정(+ play-in 팀 선택)을 확정하면
 * UPPER/LOWER 두 개의 토너먼트 경기를 생성한다.
 *
 * 라운드 구조:
 *   round=1 — 조별리그
 *   round=2 — play-in (bracketPhase=UPPER|LOWER)
 *   round=3 — 8강
 *   round=4 — 4강
 *   round=5 — 결승 + 3,4위전
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SplitBracketService {

  private final TournamentRepository tournamentRepository;
  private final TournamentMatchRepository matchRepository;
  private final BracketRepository bracketRepository;
  private final TeamRepository teamRepository;
  private final BracketQueryService bracketQueryService;

  /**
   * 상위/하위 분리 토너먼트 생성
   */
  @Transactional
  public BracketResponse generateSplitBracket(
      Long tournamentId, SplitBracketRequest request) {

    Tournament tournament = findTournament(tournamentId);

    if (tournament.getTournamentType() != TournamentType.GROUP_STAGE) {
      throw new IllegalStateException(
          "조별리그 대회에서만 분리 토너먼트를 설정할 수 있습니다.");
    }

    Bracket bracket = bracketRepository.findByTournamentId(tournamentId)
        .orElseThrow(() -> new RuntimeException("대진표 정보를 찾을 수 없습니다."));
    if (!bracket.isSplit()) {
      throw new IllegalStateException("분리 토너먼트로 설정된 대회가 아닙니다.");
    }

    // 조별리그 경기 전체 완료 여부 검증
    List<TournamentMatch> allMatches =
        matchRepository.findByTournamentIdWithTeams(tournamentId);
    boolean groupAllDone = allMatches.stream()
        .filter(m -> m.getGroupId() != null)
        .allMatch(m -> m.getStatus() == TournamentMatch.MatchStatus.FINISHED);
    if (!groupAllDone) {
      throw new IllegalStateException(
          "조별리그 경기가 모두 완료되어야 분리 토너먼트를 생성할 수 있습니다.");
    }

    // play-in 팀 수 검증 (0개 또는 2개만 허용)
    List<Long> upperPlayInIds = request.getUpperPlayInTeamIds() != null
        ? request.getUpperPlayInTeamIds() : List.of();
    List<Long> lowerPlayInIds = request.getLowerPlayInTeamIds() != null
        ? request.getLowerPlayInTeamIds() : List.of();
    if (!upperPlayInIds.isEmpty() && upperPlayInIds.size() != 2) {
      throw new IllegalArgumentException("상위 play-in 팀은 0개 또는 2개여야 합니다.");
    }
    if (!lowerPlayInIds.isEmpty() && lowerPlayInIds.size() != 2) {
      throw new IllegalArgumentException("하위 play-in 팀은 0개 또는 2개여야 합니다.");
    }

    // 기존 분리 토너먼트 경기가 이미 시작됐으면 재생성 불가
    List<TournamentMatch> existingSplit = allMatches.stream()
        .filter(m -> m.getBracketPhase() != null)
        .collect(Collectors.toList());
    boolean anyStarted = existingSplit.stream()
        .anyMatch(m -> m.getStatus() != TournamentMatch.MatchStatus.SCHEDULED);
    if (anyStarted) {
      throw new IllegalStateException(
          "이미 시작된 경기가 있어 분리 토너먼트를 재배정할 수 없습니다.");
    }
    if (!existingSplit.isEmpty()) {
      // 아직 시작 전이면 삭제 후 재생성 허용
      matchRepository.deleteAll(existingSplit);
    }

    // 팀 정보 일괄 조회
    List<Long> allTeamIds = new ArrayList<>();
    allTeamIds.addAll(request.getUpperTeamIds());
    allTeamIds.addAll(request.getLowerTeamIds());
    allTeamIds.addAll(upperPlayInIds);
    allTeamIds.addAll(lowerPlayInIds);
    Map<Long, Team> teamMap = teamRepository.findAllById(allTeamIds).stream()
        .collect(Collectors.toMap(Team::getId, t -> t));

    List<Team> upperBye = toTeamList(request.getUpperTeamIds(), teamMap);
    List<Team> upperPlayIn = toTeamList(upperPlayInIds, teamMap);
    List<Team> lowerBye = toTeamList(request.getLowerTeamIds(), teamMap);
    List<Team> lowerPlayIn = toTeamList(lowerPlayInIds, teamMap);

    List<TournamentMatch> newMatches = new ArrayList<>();
    newMatches.addAll(buildPhaseMatches(tournamentId, upperBye, upperPlayIn, "UPPER"));
    newMatches.addAll(buildPhaseMatches(tournamentId, lowerBye, lowerPlayIn, "LOWER"));
    matchRepository.saveAll(newMatches);

    bracket.markGenerated();
    bracketRepository.save(bracket);

    log.info("분리 토너먼트 생성 완료: tournamentId={}, 상위 경기={}, 하위 경기={}",
        tournamentId,
        newMatches.stream().filter(m -> "UPPER".equals(m.getBracketPhase())).count(),
        newMatches.stream().filter(m -> "LOWER".equals(m.getBracketPhase())).count());

    return bracketQueryService.getBracket(tournamentId);
  }

  /**
   * 한쪽 phase(UPPER/LOWER)의 경기 목록 생성
   */
  private List<TournamentMatch> buildPhaseMatches(
      Long tournamentId,
      List<Team> byeTeams,
      List<Team> playInTeams,
      String phase) {

    int totalTeams = byeTeams.size() + playInTeams.size();
    // 가장 가까운 2의 거듭제곱(≤ totalTeams): e.g. 9팀 → 8
    int bracketSize = largestPowerOfTwoNotExceeding(totalTeams);
    int firstKnockoutRound = 3;
    int firstRoundMatchCount = bracketSize / 2;
    int totalKnockoutRounds = (int) (Math.log(bracketSize) / Math.log(2));

    List<TournamentMatch> matches = new ArrayList<>();

    // play-in 경기 (round=2)
    if (!playInTeams.isEmpty()) {
      Team t1 = playInTeams.get(0);
      Team t2 = playInTeams.get(1);
      matches.add(TournamentMatch.builder()
          .tournamentId(tournamentId)
          .round(2)
          .matchNumber(1)
          .bracketPhase(phase)
          .team1Id(t1.getId()).team1Name(t1.getName()).team1LogoUrl(t1.getLogoUrl())
          .team2Id(t2.getId()).team2Name(t2.getName()).team2LogoUrl(t2.getLogoUrl())
          .status(TournamentMatch.MatchStatus.SCHEDULED)
          .build());
    }

    // 첫 번째 결선 라운드(8강): bye 팀 순서대로 배정, play-in 슬롯은 TBD
    int byeIdx = 0;
    for (int matchNum = 1; matchNum <= firstRoundMatchCount; matchNum++) {
      TournamentMatch.TournamentMatchBuilder b = TournamentMatch.builder()
          .tournamentId(tournamentId)
          .round(firstKnockoutRound)
          .matchNumber(matchNum)
          .bracketPhase(phase)
          .status(TournamentMatch.MatchStatus.SCHEDULED);

      if (byeIdx < byeTeams.size()) {
        Team t = byeTeams.get(byeIdx++);
        b.team1Id(t.getId()).team1Name(t.getName()).team1LogoUrl(t.getLogoUrl());
      }
      if (byeIdx < byeTeams.size()) {
        Team t = byeTeams.get(byeIdx++);
        b.team2Id(t.getId()).team2Name(t.getName()).team2LogoUrl(t.getLogoUrl());
      }
      // play-in winner가 들어올 TBD 슬롯은 null로 남겨둠

      matches.add(b.build());
    }

    // 이후 라운드(4강, 결승) — 빈 경기 생성
    int currentMatchCount = firstRoundMatchCount / 2;
    for (int round = firstKnockoutRound + 1;
         round < firstKnockoutRound + totalKnockoutRounds;
         round++) {

      for (int matchNum = 1; matchNum <= currentMatchCount; matchNum++) {
        matches.add(TournamentMatch.builder()
            .tournamentId(tournamentId)
            .round(round)
            .matchNumber(matchNum)
            .bracketPhase(phase)
            .status(TournamentMatch.MatchStatus.SCHEDULED)
            .build());
      }

      // 준결승(2경기)에서 패자는 3,4위전으로 → 마지막 라운드에 matchNumber=2 추가
      if (round == firstKnockoutRound + totalKnockoutRounds - 1
          && totalKnockoutRounds >= 2) {
        matches.add(TournamentMatch.builder()
            .tournamentId(tournamentId)
            .round(round)
            .matchNumber(2)
            .bracketPhase(phase)
            .status(TournamentMatch.MatchStatus.SCHEDULED)
            .build());
      }
      currentMatchCount /= 2;
    }

    return matches;
  }

  /** totalTeams 이하의 가장 큰 2의 거듭제곱 (e.g. 9 → 8, 8 → 8) */
  private int largestPowerOfTwoNotExceeding(int n) {
    int power = 1;
    while (power * 2 <= n) power *= 2;
    return power;
  }

  private List<Team> toTeamList(List<Long> ids, Map<Long, Team> teamMap) {
    return ids.stream()
        .map(teamMap::get)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toList());
  }

  private Tournament findTournament(Long tournamentId) {
    return tournamentRepository.findById(tournamentId)
        .orElseThrow(() -> new RuntimeException(
            "대회를 찾을 수 없습니다: " + tournamentId));
  }
}
