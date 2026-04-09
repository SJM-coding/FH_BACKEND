package com.futsal.tournament.application;

import com.futsal.team.domain.Team;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.presentation.dto.BracketResponse;
import com.futsal.tournament.presentation.dto.MatchResponse;
import com.futsal.tournament.infrastructure.TournamentGroupRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 대진표 read 서비스
 * 읽기 전용 작업 코드
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketQueryService {

  private final TournamentRepository tournamentRepository;
  private final TournamentMatchRepository matchRepository;
  private final TournamentGroupRepository groupRepository;
  private final TeamRepository teamRepository;

  /**
   * 대진표 전체 조회
   */
  @Transactional(readOnly = true)
  public BracketResponse getBracket(Long tournamentId) {
    log.info("대진표 조회 시작: tournamentId={}", tournamentId);

    Tournament tournament = tournamentRepository.findById(tournamentId)
        .orElseThrow(() -> new RuntimeException(
            "대회를 찾을 수 없습니다: " + tournamentId));

    log.info("대회 조회 완료: id={}, type={}, bracketType={}, bracketGenerated={}",
        tournament.getId(), tournament.getTournamentType(),
        tournament.getBracketType(), tournament.getBracketGenerated());

    if (!Boolean.TRUE.equals(tournament.getBracketGenerated())) {
      return BracketResponse.builder()
          .tournamentId(tournament.getId())
          .tournamentTitle(tournament.getTitle())
          .tournamentType(tournament.getTournamentType() != null
              ? tournament.getTournamentType().name() : null)
          .bracketType(tournament.getBracketType() != null
              ? tournament.getBracketType().name() : BracketType.AUTO.name())
          .bracketGenerated(false)
          .build();
    }

    // MANUAL 타입: 이미지 URL만 반환
    if (tournament.isManualBracket()) {
      List<String> imageUrls = new ArrayList<>(tournament.getBracketImageUrls());
      log.info("수동 대진표 조회: {}개 이미지", imageUrls.size());
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

    List<TournamentMatch> allMatches =
        matchRepository.findByTournamentIdWithTeams(tournamentId);
    log.info("경기 조회 완료: {}개 경기", allMatches.size());

    BracketResponse.BracketResponseBuilder builder = BracketResponse.builder()
        .tournamentId(tournament.getId())
        .tournamentTitle(tournament.getTitle())
        .tournamentType(tournament.getTournamentType().name())
        .bracketType(BracketType.AUTO.name())
        .bracketGenerated(tournament.getBracketGenerated());

    return switch (tournament.getTournamentType()) {
      case SINGLE_ELIMINATION ->
          buildSingleEliminationBracket(builder, allMatches);
      case GROUP_STAGE ->
          buildGroupStageBracket(builder, tournament, allMatches);
      case SWISS_SYSTEM ->
          buildSwissSystemBracket(builder, allMatches);
      default -> throw new RuntimeException("지원하지 않는 대회 유형입니다.");
    };
  }

  // ── 대회 유형별 응답 조립 ─────────────────────────────────────────

  private BracketResponse buildSingleEliminationBracket(
      BracketResponse.BracketResponseBuilder builder,
      List<TournamentMatch> matches) {

    Map<Integer, List<TournamentMatch>> matchesByRound = matches.stream()
        .collect(Collectors.groupingBy(TournamentMatch::getRound));

    Integer maxRound = matchesByRound.keySet().stream()
        .max(Integer::compareTo).orElse(0);

    List<BracketResponse.RoundMatches> rounds = new ArrayList<>();
    for (int round = 1; round <= maxRound; round++) {
      List<TournamentMatch> roundMatches =
          matchesByRound.getOrDefault(round, Collections.emptyList());
      rounds.add(BracketResponse.RoundMatches.builder()
          .round(round)
          .roundName(getRoundDisplayName(round, maxRound, roundMatches))
          .matches(toMatchResponses(roundMatches, round, maxRound))
          .build());
    }

    return builder
        .totalRounds(maxRound)
        .currentRound(getCurrentRound(matchesByRound))
        .rounds(rounds)
        .build();
  }

  private BracketResponse buildGroupStageBracket(
      BracketResponse.BracketResponseBuilder builder,
      Tournament tournament,
      List<TournamentMatch> matches) {

    log.info("조별리그 대진표 구성 시작: tournamentId={}", tournament.getId());
    List<TournamentGroup> groups =
        groupRepository.findByTournamentIdWithTeams(tournament.getId());
    log.info("조 조회 완료: {}개 조", groups.size());

    List<BracketResponse.GroupInfo> groupInfos = new ArrayList<>();
    boolean allGroupsCompleted = true;
    boolean anyGroupHasTie = false;

    for (TournamentGroup group : groups) {
      List<TournamentMatch> groupMatches = matches.stream()
          .filter(m -> group.getGroupName().equals(m.getGroupId()))
          .collect(Collectors.toList());

      List<BracketResponse.TeamStanding> standings =
          calculateStandings(group, groupMatches);

      boolean groupCompleted =
          groupMatches.stream().allMatch(TournamentMatch::isFinished);
      if (!groupCompleted) {
        allGroupsCompleted = false;
      }

      boolean hasTie = false;
      List<Long> tiedTeamIds = new ArrayList<>();
      if (groupCompleted && standings.size() >= 3) {
        int secondPoints = standings.get(1).getPoints();
        int thirdPoints = standings.get(2).getPoints();
        if (secondPoints == thirdPoints) {
          hasTie = true;
          anyGroupHasTie = true;
          for (BracketResponse.TeamStanding s : standings) {
            if (s.getPoints() == secondPoints) {
              tiedTeamIds.add(s.getTeamId());
            }
          }
        }
      }

      groupInfos.add(BracketResponse.GroupInfo.builder()
          .groupName(group.getGroupName())
          .standings(standings)
          .matches(groupMatches.stream()
              .map(MatchResponse::from)
              .collect(Collectors.toList()))
          .groupCompleted(groupCompleted)
          .hasTie(hasTie)
          .tiedTeamIds(tiedTeamIds)
          .build());
    }

    List<TournamentMatch> knockoutMatches = matches.stream()
        .filter(m -> m.getGroupId() == null && m.getRound() > 1)
        .collect(Collectors.toList());

    Map<Integer, List<TournamentMatch>> knockoutByRound = knockoutMatches.stream()
        .collect(Collectors.groupingBy(TournamentMatch::getRound));

    List<BracketResponse.RoundMatches> rounds = knockoutByRound.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> BracketResponse.RoundMatches.builder()
            .round(entry.getKey())
            .roundName("결선 " + getRoundDisplayName(
                entry.getKey() - 1,
                knockoutByRound.size(),
                entry.getValue()))
            .matches(toMatchResponses(
                entry.getValue(),
                entry.getKey() - 1,
                knockoutByRound.size()))
            .build())
        .collect(Collectors.toList());

    boolean knockoutTeamsAssigned = knockoutMatches.stream()
        .anyMatch(m -> m.getTeam1Id() != null || m.getTeam2Id() != null);

    return builder
        .groups(groupInfos)
        .rounds(rounds)
        .groupStageCompleted(allGroupsCompleted)
        .needsQualifierSelection(
            allGroupsCompleted && anyGroupHasTie && !knockoutTeamsAssigned)
        .knockoutGenerated(knockoutTeamsAssigned)
        .build();
  }

  private BracketResponse buildSwissSystemBracket(
      BracketResponse.BracketResponseBuilder builder,
      List<TournamentMatch> matches) {

    Map<Integer, List<TournamentMatch>> matchesByRound = matches.stream()
        .collect(Collectors.groupingBy(TournamentMatch::getRound));
    Integer maxRound = matchesByRound.keySet().stream()
        .max(Integer::compareTo).orElse(0);

    List<BracketResponse.RoundMatches> rounds = new ArrayList<>();
    for (int round = 1; round <= maxRound; round++) {
      List<TournamentMatch> roundMatches =
          matchesByRound.getOrDefault(round, Collections.emptyList());
      rounds.add(BracketResponse.RoundMatches.builder()
          .round(round)
          .roundName("라운드 " + round)
          .matches(roundMatches.stream()
              .map(MatchResponse::from)
              .collect(Collectors.toList()))
          .build());
    }

    return builder
        .totalRounds(maxRound)
        .currentRound(getCurrentRound(matchesByRound))
        .rounds(rounds)
        .build();
  }

  // ── 조별 순위 계산 (Command 서비스에서도 사용) ──────────────────────

  /**
   * 조별 순위표 계산 (승점 > 골득실 > 다득점 순)
   */
  List<BracketResponse.TeamStanding> calculateStandings(
      TournamentGroup group,
      List<TournamentMatch> matches) {

    Map<Long, BracketResponse.TeamStanding> standingsMap = new HashMap<>();

    Map<Long, Team> teamMap = teamRepository.findAllById(group.getTeamIds())
        .stream().collect(Collectors.toMap(Team::getId, t -> t));
    group.getTeamIds().forEach(teamId -> {
      Team team = teamMap.get(teamId);
      standingsMap.put(teamId, BracketResponse.TeamStanding.builder()
          .teamId(teamId)
          .teamName(team != null ? team.getName() : "알 수 없음")
          .teamLogoUrl(team != null ? team.getLogoUrl() : null)
          .played(0).won(0).drawn(0).lost(0)
          .goalsFor(0).goalsAgainst(0).goalDifference(0).points(0)
          .build());
    });

    matches.stream()
        .filter(TournamentMatch::isFinished)
        .forEach(match -> {
          if (match.getTeam1Id() == null || match.getTeam2Id() == null) return;
          if (match.getTeam1Score() == null || match.getTeam2Score() == null) return;

          Long t1 = match.getTeam1Id();
          Long t2 = match.getTeam2Id();
          BracketResponse.TeamStanding s1 = standingsMap.get(t1);
          BracketResponse.TeamStanding s2 = standingsMap.get(t2);
          if (s1 == null || s2 == null) return;

          s1.setPlayed(s1.getPlayed() + 1);
          s2.setPlayed(s2.getPlayed() + 1);
          s1.setGoalsFor(s1.getGoalsFor() + match.getTeam1Score());
          s1.setGoalsAgainst(s1.getGoalsAgainst() + match.getTeam2Score());
          s2.setGoalsFor(s2.getGoalsFor() + match.getTeam2Score());
          s2.setGoalsAgainst(s2.getGoalsAgainst() + match.getTeam1Score());

          if (match.getWinnerId() == null) {
            s1.setDrawn(s1.getDrawn() + 1);
            s2.setDrawn(s2.getDrawn() + 1);
            s1.setPoints(s1.getPoints() + 1);
            s2.setPoints(s2.getPoints() + 1);
          } else if (match.getWinnerId().equals(t1)) {
            s1.setWon(s1.getWon() + 1);
            s2.setLost(s2.getLost() + 1);
            s1.setPoints(s1.getPoints() + 3);
          } else {
            s2.setWon(s2.getWon() + 1);
            s1.setLost(s1.getLost() + 1);
            s2.setPoints(s2.getPoints() + 3);
          }

          s1.setGoalDifference(s1.getGoalsFor() - s1.getGoalsAgainst());
          s2.setGoalDifference(s2.getGoalsFor() - s2.getGoalsAgainst());
        });

    List<BracketResponse.TeamStanding> sorted = standingsMap.values().stream()
        .sorted(Comparator
            .comparing(BracketResponse.TeamStanding::getPoints).reversed()
            .thenComparing(BracketResponse.TeamStanding::getGoalDifference)
            .reversed()
            .thenComparing(BracketResponse.TeamStanding::getGoalsFor)
            .reversed())
        .collect(Collectors.toList());

    for (int i = 0; i < sorted.size(); i++) {
      sorted.get(i).setRank(i + 1);
    }
    return sorted;
  }

  // ── 유틸리티 ─────────────────────────────────────────────────────

  private String getRoundName(int round, int totalRounds) {
    int teamsInRound = (int) Math.pow(2, totalRounds - round + 1);
    if (round == totalRounds) return "결승";
    if (round == totalRounds - 1) return "준결승";
    if (round == totalRounds - 2) return "준준결승";
    return teamsInRound + "강";
  }

  private String getRoundDisplayName(
      int round, int totalRounds, List<TournamentMatch> roundMatches) {
    String baseName = getRoundName(round, totalRounds);
    if (round == totalRounds && roundMatches.size() >= 2) {
      return "결승 / 3·4위전";
    }
    return baseName;
  }

  private List<MatchResponse> toMatchResponses(
      List<TournamentMatch> matches, int round, int totalRounds) {

    List<TournamentMatch> sorted = matches.stream()
        .sorted(Comparator.comparing(TournamentMatch::getMatchNumber))
        .collect(Collectors.toList());

    List<MatchResponse> responses = sorted.stream()
        .map(MatchResponse::from)
        .collect(Collectors.toList());

    if (round == totalRounds && sorted.size() >= 2) {
      for (int i = 0; i < responses.size(); i++) {
        TournamentMatch match = sorted.get(i);
        if (match.getMatchNumber() == 1) {
          responses.get(i).setMatchLabel("결승");
        } else if (match.getMatchNumber() == 2) {
          responses.get(i).setMatchLabel("3·4위전");
        }
      }
    }
    return responses;
  }

  private Integer getCurrentRound(
      Map<Integer, List<TournamentMatch>> matchesByRound) {
    List<Integer> rounds = new ArrayList<>(matchesByRound.keySet());
    Collections.sort(rounds);
    for (Integer round : rounds) {
      boolean hasUnfinished = matchesByRound.get(round).stream()
          .anyMatch(m -> m.getStatus() != TournamentMatch.MatchStatus.FINISHED);
      if (hasUnfinished) return round;
    }
    return rounds.isEmpty() ? 1 : rounds.get(rounds.size() - 1);
  }
}
