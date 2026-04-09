package com.futsal.tournament.application;

import com.futsal.shared.infrastructure.S3Service;
import com.futsal.team.domain.Team;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.presentation.dto.*;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.tournament.infrastructure.TournamentGroupRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 대진표 write 서비스
 * 일정 등록, 결과 입력, 이미지 업로드, 진출팀 배정 등 C,U,D 작업
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketCommandService {

  private final TournamentRepository tournamentRepository;
  private final TournamentMatchRepository matchRepository;
  private final TournamentGroupRepository groupRepository;
  private final TeamRepository teamRepository;
  private final S3Service s3Service;
  private final BracketQueryService bracketQueryService;
  private final BracketRepository bracketRepository;

  // ── 경기 일정 ──────────────────────────────────────────────────────

  /**
   * 경기 일정 단건 업데이트
   */
  @Transactional
  public MatchResponse updateMatchSchedule(
      Long tournamentId, Long matchId, MatchScheduleRequest request) {

    TournamentMatch match = findMatch(matchId);
    verifyMatchBelongsTo(match, tournamentId);
    match.updateSchedule(request.getScheduledAt(), request.getVenueName());
    TournamentMatch saved = matchRepository.save(match);
    log.info("경기 일정 업데이트: matchId={}, scheduledAt={}, venueName={}",
        matchId, request.getScheduledAt(), request.getVenueName());
    return MatchResponse.from(saved);
  }

  /**
   * 경기 일정 일괄 업데이트
   */
  @Transactional
  public List<MatchResponse> updateMatchSchedules(
      Long tournamentId, BatchMatchScheduleRequest request) {

    if (request.getSchedules() == null || request.getSchedules().isEmpty()) {
      throw new RuntimeException("저장할 경기 일정이 없습니다.");
    }

    List<Long> matchIds = request.getSchedules().stream()
        .map(MatchScheduleUpdateRequest::getMatchId)
        .toList();

    List<TournamentMatch> matches = matchRepository.findAllById(matchIds);
    if (matches.size() != matchIds.size()) {
      throw new RuntimeException("일부 경기를 찾을 수 없습니다.");
    }

    Map<Long, TournamentMatch> matchesById = matches.stream()
        .collect(Collectors.toMap(TournamentMatch::getId, m -> m));

    List<TournamentMatch> updated = new ArrayList<>();
    for (MatchScheduleUpdateRequest schedule : request.getSchedules()) {
      TournamentMatch match = matchesById.get(schedule.getMatchId());
      if (match == null) {
        throw new RuntimeException("경기를 찾을 수 없습니다: " + schedule.getMatchId());
      }
      verifyMatchBelongsTo(match, tournamentId);
      match.updateSchedule(schedule.getScheduledAt(), schedule.getVenueName());
      updated.add(match);
    }

    List<TournamentMatch> saved = matchRepository.saveAll(updated);
    log.info("경기 일정 일괄 업데이트: tournamentId={}, matchCount={}",
        tournamentId, saved.size());
    return saved.stream().map(MatchResponse::from).toList();
  }

  // ── 경기 결과 ──────────────────────────────────────────────────────

  /**
   * 경기 결과 입력
   */
  @Transactional
  public MatchResponse recordMatchResult(
      Long tournamentId, Long matchId, MatchResultRequest request) {

    Tournament tournament = findTournament(tournamentId);
    TournamentMatch match = findMatch(matchId);
    verifyMatchBelongsTo(match, tournamentId);

    // Tournament에서 경기 유형 판단 (컨텍스트 제공)
    boolean isKnockout = tournament.isKnockoutMatch(match);

    // Match Aggregate가 자신의 불변식 검증 후 결과 기록
    match.recordResult(
        request.getTeam1Score(),
        request.getTeam2Score(),
        request.getTeam1PenaltyScore(),
        request.getTeam2PenaltyScore(),
        isKnockout
    );
    matchRepository.save(match);

    if (tournament.getTournamentType() == TournamentType.SINGLE_ELIMINATION) {
      advanceWinnerToNextRound(match);
    } else if (tournament.getTournamentType() == TournamentType.GROUP_STAGE) {
      if (match.getGroupId() != null) {
        checkAndGenerateKnockoutBracket(tournament);
      } else {
        advanceWinnerToNextRound(match);
      }
    }

    return MatchResponse.from(match);
  }

  // ── 대진표 이미지 ──────────────────────────────────────────────────

  /**
   * 대진표 이미지 업로드 (수동 대진표로 전환)
   */
  @Transactional
  public BracketResponse uploadBracketImages(
      Long tournamentId, List<MultipartFile> files, User user) {

    Tournament tournament = findTournament(tournamentId);
    verifyOwner(tournament, user);

    // 기존 자동 생성 데이터 삭제
    if (!tournament.isManualBracket()
        && Boolean.TRUE.equals(tournament.getBracketGenerated())) {
      log.info("기존 자동 생성 대진표 삭제: tournamentId={}", tournamentId);
      matchRepository.deleteByTournamentId(tournamentId);
    }

    List<String> imageUrls = new ArrayList<>();
    for (MultipartFile file : files) {
      imageUrls.add(s3Service.uploadBracketImage(file));
    }

    // Tournament 필드 업데이트 (하위 호환)
    tournament.switchToManualBracket(imageUrls);
    tournamentRepository.save(tournament);

    // Bracket Aggregate 업데이트 (dual write)
    Bracket bracket = bracketRepository.findByTournamentId(tournamentId)
        .orElseGet(() -> Bracket.createDefault(tournamentId));
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

    // Tournament 필드 업데이트 (하위 호환)
    tournament.switchToAutoBracket();
    tournamentRepository.save(tournament);

    // Bracket Aggregate 업데이트 (dual write)
    bracketRepository.findByTournamentId(tournamentId).ifPresent(bracket -> {
      bracket.switchToAuto();
      bracketRepository.save(bracket);
    });

    log.info("대진표 이미지 삭제 완료, AUTO 모드로 전환: tournamentId={}", tournamentId);
  }

  // ── 진출팀 선택 ────────────────────────────────────────────────────

  /**
   * 조별리그 진출팀 수동 선택 (개최자 인증 필요)
   */
  @Transactional
  public BracketResponse selectQualifiersAndGenerateKnockout(
      Long tournamentId, QualifierSelectionRequest request, User user) {

    Tournament tournament = findTournament(tournamentId);
    verifyOwner(tournament, user);
    return selectQualifiersInternal(tournament, request);
  }

  /**
   * 조별리그 진출팀 수동 선택 (운영진 코드 인증)
   */
  @Transactional
  public BracketResponse selectQualifiersAndGenerateKnockoutByShareCode(
      Long tournamentId, QualifierSelectionRequest request) {

    Tournament tournament = findTournament(tournamentId);
    return selectQualifiersInternal(tournament, request);
  }

  private BracketResponse selectQualifiersInternal(
      Tournament tournament, QualifierSelectionRequest request) {

    Long tournamentId = tournament.getId();

    if (tournament.getTournamentType() != TournamentType.GROUP_STAGE) {
      throw new RuntimeException("조별리그 대회에서만 사용할 수 있습니다.");
    }

    List<TournamentMatch> allMatches =
        matchRepository.findByTournamentIdWithTeams(tournamentId);

    List<TournamentMatch> knockoutMatches = allMatches.stream()
        .filter(m -> m.getGroupId() == null && m.getRound() > 1)
        .collect(Collectors.toList());

    boolean knockoutAssigned = knockoutMatches.stream()
        .anyMatch(m -> m.getTeam1() != null || m.getTeam2() != null);
    if (knockoutAssigned) {
      throw new RuntimeException("이미 결선 토너먼트 팀이 배정되었습니다.");
    }

    List<TournamentMatch> groupMatches = allMatches.stream()
        .filter(m -> m.getGroupId() != null)
        .collect(Collectors.toList());
    boolean allFinished = groupMatches.stream()
        .allMatch(m -> m.getStatus() == TournamentMatch.MatchStatus.FINISHED);
    if (!allFinished) {
      throw new RuntimeException("모든 조별리그 경기가 종료되어야 합니다.");
    }

    List<TournamentGroup> groups =
        groupRepository.findByTournamentIdWithTeams(tournamentId);
    if (request.getQualifiedTeamsByGroup() == null
        || request.getQualifiedTeamsByGroup().isEmpty()) {
      throw new RuntimeException("각 조별 진출팀을 선택해주세요.");
    }

    List<Team> qualifiedTeams = new ArrayList<>();
    for (TournamentGroup group : groups) {
      String groupName = group.getGroupName();
      List<Long> selectedIds =
          request.getQualifiedTeamsByGroup().get(groupName);
      if (selectedIds == null || selectedIds.isEmpty()) {
        throw new RuntimeException("조 " + groupName + "의 진출팀을 선택해주세요.");
      }

      Set<Long> groupTeamIds = group.getTeams().stream()
          .map(Team::getId).collect(Collectors.toSet());

      for (Long teamId : selectedIds) {
        if (!groupTeamIds.contains(teamId)) {
          throw new RuntimeException(
              "팀 ID " + teamId + "는 조 " + groupName + "에 속하지 않습니다.");
        }
        qualifiedTeams.add(
            teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException(
                    "팀을 찾을 수 없습니다: " + teamId)));
      }
    }

    log.info("수동 선택된 결선 진출팀: {}팀", qualifiedTeams.size());
    assignTeamsToKnockoutMatches(
        tournament, qualifiedTeams, knockoutMatches, groups.size());

    return bracketQueryService.getBracket(tournamentId);
  }

  // ── 결선 대진 수동 재배치 ──────────────────────────────────────────

  /**
   * 조별리그 결선 1라운드 팀 수동 재배치
   */
  @Transactional
  public BracketResponse updateKnockoutAssignments(
      Long tournamentId, KnockoutMatchAssignmentRequest request, User user) {

    Tournament tournament = findTournament(tournamentId);
    verifyOwner(tournament, user);

    if (tournament.getTournamentType() != TournamentType.GROUP_STAGE) {
      throw new RuntimeException("조별리그 결선 토너먼트에서만 사용할 수 있습니다.");
    }
    if (!Boolean.TRUE.equals(tournament.getBracketGenerated())
        || tournament.isManualBracket()) {
      throw new RuntimeException("자동 생성된 대진표에서만 사용할 수 있습니다.");
    }

    List<TournamentMatch> allMatches =
        matchRepository.findByTournamentIdWithTeams(tournamentId);
    List<TournamentMatch> knockoutMatches = allMatches.stream()
        .filter(m -> m.getGroupId() == null && m.getRound() > 1)
        .collect(Collectors.toList());

    if (knockoutMatches.isEmpty()) {
      throw new RuntimeException("결선 토너먼트가 아직 생성되지 않았습니다.");
    }

    boolean knockoutStarted = knockoutMatches.stream()
        .anyMatch(m -> m.getStatus() != TournamentMatch.MatchStatus.SCHEDULED);
    if (knockoutStarted) {
      throw new RuntimeException("결선 경기가 시작된 이후에는 대진을 수정할 수 없습니다.");
    }

    int firstKnockoutRound = knockoutMatches.stream()
        .map(TournamentMatch::getRound)
        .min(Integer::compareTo)
        .orElseThrow(() -> new RuntimeException("결선 라운드를 찾을 수 없습니다."));

    List<TournamentMatch> firstRoundMatches = knockoutMatches.stream()
        .filter(m -> m.getRound() == firstKnockoutRound)
        .sorted(Comparator.comparing(TournamentMatch::getMatchNumber))
        .collect(Collectors.toList());

    if (firstRoundMatches.isEmpty()) {
      throw new RuntimeException("결선 1라운드 경기를 찾을 수 없습니다.");
    }
    if (firstRoundMatches.stream()
        .anyMatch(m -> m.getTeam1() == null || m.getTeam2() == null)) {
      throw new RuntimeException("아직 팀 배정이 완료되지 않은 결선 경기입니다.");
    }
    if (request == null || request.getAssignments() == null
        || request.getAssignments().isEmpty()) {
      throw new RuntimeException("수정할 결선 대진 정보가 없습니다.");
    }

    Map<Long, TournamentMatch> firstRoundMatchMap = firstRoundMatches.stream()
        .collect(Collectors.toMap(TournamentMatch::getId, m -> m));

    if (request.getAssignments().size() != firstRoundMatches.size()) {
      throw new RuntimeException(
          "결선 1라운드 경기 수와 요청 데이터가 일치하지 않습니다.");
    }

    Set<Long> currentTeamIds = firstRoundMatches.stream()
        .flatMap(m -> java.util.stream.Stream.of(m.getTeam1(), m.getTeam2()))
        .map(Team::getId)
        .collect(Collectors.toSet());

    Set<Long> requestedMatchIds = new HashSet<>();
    Set<Long> requestedTeamIds = new HashSet<>();

    for (KnockoutMatchAssignmentRequest.MatchAssignment assignment
        : request.getAssignments()) {
      if (assignment.getMatchId() == null) {
        throw new RuntimeException("경기 ID가 누락되었습니다.");
      }
      if (!requestedMatchIds.add(assignment.getMatchId())) {
        throw new RuntimeException("같은 경기가 중복 요청되었습니다.");
      }
      if (firstRoundMatchMap.get(assignment.getMatchId()) == null) {
        throw new RuntimeException("결선 1라운드가 아닌 경기가 포함되어 있습니다.");
      }
      Long t1 = assignment.getTeam1Id();
      Long t2 = assignment.getTeam2Id();
      if (t1 == null || t2 == null) {
        throw new RuntimeException("각 경기에는 양 팀이 모두 지정되어야 합니다.");
      }
      if (t1.equals(t2)) {
        throw new RuntimeException("한 경기에는 같은 팀을 중복 배정할 수 없습니다.");
      }
      requestedTeamIds.add(t1);
      requestedTeamIds.add(t2);
    }

    if (!requestedMatchIds.equals(firstRoundMatchMap.keySet())) {
      throw new RuntimeException(
          "결선 1라운드 전체 경기에 대한 배정 정보가 필요합니다.");
    }
    if (!requestedTeamIds.equals(currentTeamIds)) {
      throw new RuntimeException(
          "현재 결선 진출팀 범위 안에서만 대진을 재배치할 수 있습니다.");
    }

    Map<Long, Team> teamMap = teamRepository.findAllById(currentTeamIds).stream()
        .collect(Collectors.toMap(Team::getId, t -> t));

    for (KnockoutMatchAssignmentRequest.MatchAssignment assignment
        : request.getAssignments()) {
      TournamentMatch match = firstRoundMatchMap.get(assignment.getMatchId());
      Team team1 = teamMap.get(assignment.getTeam1Id());
      Team team2 = teamMap.get(assignment.getTeam2Id());
      if (team1 == null || team2 == null) {
        throw new RuntimeException("결선 진출팀 정보를 찾을 수 없습니다.");
      }
      match.assignTeam1(team1);
      match.assignTeam2(team2);
      matchRepository.save(match);
    }

    return bracketQueryService.getBracket(tournamentId);
  }

  // ── 내부 도우미 ────────────────────────────────────────────────────

  private void advanceWinnerToNextRound(TournamentMatch match) {
    if (match.getWinner() == null) {
      log.warn("무승부 경기는 진출 처리할 수 없습니다: matchId={}", match.getId());
      return;
    }

    int nextRound = match.getRound() + 1;
    int nextMatchNumber = (match.getMatchNumber() + 1) / 2;

    List<TournamentMatch> nextMatches = matchRepository.findByTournamentIdAndRound(
        match.getTournament().getId(), nextRound);

    nextMatches.stream()
        .filter(m -> m.getMatchNumber() == nextMatchNumber)
        .findFirst()
        .ifPresent(next -> {
          if (match.getMatchNumber() % 2 == 1) {
            next.assignTeam1(match.getWinner());
          } else {
            next.assignTeam2(match.getWinner());
          }
          matchRepository.save(next);
          log.info("승자 진출 처리: {}팀 -> {}라운드 {}경기",
              match.getWinner().getName(), nextRound, nextMatchNumber);
        });

    assignLoserToThirdPlaceMatch(match, nextRound);
  }

  private void assignLoserToThirdPlaceMatch(TournamentMatch match, int nextRound) {
    Team loser = match.getLoser();
    if (loser == null) return;

    List<TournamentMatch> currentRoundMatches =
        matchRepository.findByTournamentIdAndRound(
            match.getTournament().getId(), match.getRound());
    if (currentRoundMatches.size() != 2) return;

    List<TournamentMatch> nextRoundMatches =
        matchRepository.findByTournamentIdAndRound(
            match.getTournament().getId(), nextRound);
    if (nextRoundMatches.size() < 2) return;

    nextRoundMatches.stream()
        .filter(m -> m.getMatchNumber() == 2)
        .findFirst()
        .ifPresent(third -> {
          if (match.getMatchNumber() % 2 == 1) {
            third.assignTeam1(loser);
          } else {
            third.assignTeam2(loser);
          }
          matchRepository.save(third);
          log.info("패자 3,4위전 배치: {}팀 -> {}라운드 2경기",
              loser.getName(), nextRound);
        });
  }

  /**
   * 조별리그 완료 시 결선 팀 자동 배정 (동점 없는 경우에만)
   */
  private void checkAndGenerateKnockoutBracket(Tournament tournament) {
    List<TournamentMatch> allMatches =
        matchRepository.findByTournamentIdWithTeams(tournament.getId());

    List<TournamentMatch> knockoutMatches = allMatches.stream()
        .filter(m -> m.getGroupId() == null && m.getRound() > 1)
        .collect(Collectors.toList());

    boolean knockoutAssigned = knockoutMatches.stream()
        .anyMatch(m -> m.getTeam1() != null || m.getTeam2() != null);
    if (knockoutAssigned) return;

    List<TournamentMatch> groupMatches = allMatches.stream()
        .filter(m -> m.getGroupId() != null)
        .collect(Collectors.toList());
    boolean allFinished = groupMatches.stream()
        .allMatch(m -> m.getStatus() == TournamentMatch.MatchStatus.FINISHED);
    if (!allFinished) return;

    List<TournamentGroup> groups =
        groupRepository.findByTournamentIdWithTeams(tournament.getId());

    // 동점 체크: 있으면 자동 배정하지 않음 (개최자가 수동 선택)
    for (TournamentGroup group : groups) {
      List<TournamentMatch> gMatches = groupMatches.stream()
          .filter(m -> group.getGroupName().equals(m.getGroupId()))
          .collect(Collectors.toList());
      List<BracketResponse.TeamStanding> standings =
          bracketQueryService.calculateStandings(group, gMatches);

      if (standings.size() >= 3
          && standings.get(1).getPoints() == standings.get(2).getPoints()) {
        log.info("조별리그 {} 동점 발견: 수동 선택 필요", group.getGroupName());
        return;
      }
    }

    log.info("조별리그 완료, 동점 없음. 결선 토너먼트 팀 배정 시작: tournamentId={}",
        tournament.getId());

    List<Team> qualifiedTeams = new ArrayList<>();
    for (TournamentGroup group : groups) {
      List<TournamentMatch> gMatches = groupMatches.stream()
          .filter(m -> group.getGroupName().equals(m.getGroupId()))
          .collect(Collectors.toList());
      List<BracketResponse.TeamStanding> standings =
          bracketQueryService.calculateStandings(group, gMatches);

      int advanceCount = Math.min(tournament.getAdvanceCount(), standings.size());
      for (int i = 0; i < advanceCount; i++) {
        Long teamId = standings.get(i).getTeamId();
        group.getTeams().stream()
            .filter(t -> t.getId().equals(teamId))
            .findFirst()
            .ifPresent(qualifiedTeams::add);
      }
    }

    log.info("결선 진출팀 {}팀 선정 완료", qualifiedTeams.size());
    assignTeamsToKnockoutMatches(
        tournament, qualifiedTeams, knockoutMatches, groups.size());
  }

  private void assignTeamsToKnockoutMatches(
      Tournament tournament,
      List<Team> qualifiedTeams,
      List<TournamentMatch> knockoutMatches,
      int groupCount) {

    int teamCount = qualifiedTeams.size();
    if (teamCount < 2) {
      log.warn("결선 진출팀이 2팀 미만입니다: {}", teamCount);
      return;
    }

    int bracketSize = 1;
    while (bracketSize < teamCount) bracketSize *= 2;

    log.info("결선 토너먼트 팀 배정: {}팀", teamCount);

    // 크로스 시드 배치
    List<Team> seededTeams = new ArrayList<>();
    int teamsPerGroup = teamCount / groupCount;
    for (int i = 0; i < teamsPerGroup; i++) {
      for (int g = 0; g < groupCount; g++) {
        int idx = g * teamsPerGroup + i;
        if (idx < qualifiedTeams.size()) {
          seededTeams.add(qualifiedTeams.get(idx));
        }
      }
    }

    List<TournamentMatch> firstRoundMatches = knockoutMatches.stream()
        .filter(m -> m.getRound() == 2)
        .sorted(Comparator.comparing(TournamentMatch::getMatchNumber))
        .collect(Collectors.toList());

    for (int i = 0; i < firstRoundMatches.size(); i++) {
      TournamentMatch match = firstRoundMatches.get(i);
      int t1Idx = i;
      int t2Idx = bracketSize - 1 - i;
      if (t1Idx < seededTeams.size()) match.assignTeam1(seededTeams.get(t1Idx));
      if (t2Idx < seededTeams.size()) match.assignTeam2(seededTeams.get(t2Idx));
      matchRepository.save(match);
    }

    log.info("결선 토너먼트 팀 배정 완료: {}경기", firstRoundMatches.size());
  }

  private Tournament findTournament(Long tournamentId) {
    return tournamentRepository.findById(tournamentId)
        .orElseThrow(() -> new RuntimeException(
            "대회를 찾을 수 없습니다: " + tournamentId));
  }

  private TournamentMatch findMatch(Long matchId) {
    return matchRepository.findById(matchId)
        .orElseThrow(() -> new RuntimeException(
            "경기를 찾을 수 없습니다: " + matchId));
  }

  private void verifyMatchBelongsTo(TournamentMatch match, Long tournamentId) {
    if (!match.getTournament().getId().equals(tournamentId)) {
      throw new RuntimeException("대회 정보가 일치하지 않습니다.");
    }
  }

  private void verifyOwner(Tournament tournament, User user) {
    if (!tournament.isRegisteredBy(user.getId())) {
      throw new RuntimeException("대진표를 수정할 권한이 없습니다.");
    }
  }
}
