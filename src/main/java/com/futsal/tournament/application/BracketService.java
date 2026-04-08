package com.futsal.tournament.application;

import com.futsal.common.storage.S3Service;
import com.futsal.team.domain.Team;
import com.futsal.team.repository.TeamRepository;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.presentation.dto.*;
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
 * @deprecated BracketQueryService, BracketCommandService로 분리됨.
 *             이 클래스는 다음 Phase에서 제거될 예정입니다.
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketService {

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentGroupRepository groupRepository;
    private final TeamRepository teamRepository;
    private final S3Service s3Service;

    /**
     * 대진표 전체 조회
     */
    @Transactional(readOnly = true)
    public BracketResponse getBracket(Long tournamentId) {
        log.info("대진표 조회 시작: tournamentId={}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        log.info("대회 조회 완료: id={}, type={}, bracketType={}, bracketGenerated={}",
            tournament.getId(), tournament.getTournamentType(),
            tournament.getBracketType(), tournament.getBracketGenerated());

        // 대진표 미생성 상태
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
            // Hibernate 프록시가 아닌 새 ArrayList로 복사 (Redis 캐시 직렬화 문제 방지)
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

        // AUTO 타입: 기존 로직
        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournamentId);
        log.info("경기 조회 완료: {}개 경기", allMatches.size());

        BracketResponse.BracketResponseBuilder builder = BracketResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentTitle(tournament.getTitle())
                .tournamentType(tournament.getTournamentType().name())
                .bracketType(BracketType.AUTO.name())
                .bracketGenerated(tournament.getBracketGenerated());

        switch (tournament.getTournamentType()) {
            case SINGLE_ELIMINATION:
                return buildSingleEliminationBracket(builder, allMatches);
            case GROUP_STAGE:
                return buildGroupStageBracket(builder, tournament, allMatches);
            case SWISS_SYSTEM:
                return buildSwissSystemBracket(builder, allMatches);
            default:
                throw new RuntimeException("지원하지 않는 대회 유형입니다.");
        }
    }

    /**
     * 싱글 엘리미네이션 대진표 구성
     */
    private BracketResponse buildSingleEliminationBracket(
            BracketResponse.BracketResponseBuilder builder,
            List<TournamentMatch> matches) {
        
        // 라운드별 그룹핑
        Map<Integer, List<TournamentMatch>> matchesByRound = matches.stream()
                .collect(Collectors.groupingBy(TournamentMatch::getRound));

        Integer maxRound = matchesByRound.keySet().stream().max(Integer::compareTo).orElse(0);
        
        List<BracketResponse.RoundMatches> rounds = new ArrayList<>();
        for (int round = 1; round <= maxRound; round++) {
            List<TournamentMatch> roundMatches = matchesByRound.getOrDefault(round, Collections.emptyList());

            BracketResponse.RoundMatches roundData = BracketResponse.RoundMatches.builder()
                    .round(round)
                    .roundName(getRoundDisplayName(round, maxRound, roundMatches))
                    .matches(toMatchResponses(roundMatches, round, maxRound))
                    .build();

            rounds.add(roundData);
        }

        return builder
                .totalRounds(maxRound)
                .currentRound(getCurrentRound(matchesByRound))
                .rounds(rounds)
                .build();
    }

    /**
     * 조별리그 대진표 구성
     */
    private BracketResponse buildGroupStageBracket(
            BracketResponse.BracketResponseBuilder builder,
            Tournament tournament,
            List<TournamentMatch> matches) {

        log.info("조별리그 대진표 구성 시작: tournamentId={}", tournament.getId());
        List<TournamentGroup> groups = groupRepository.findByTournamentIdWithTeams(tournament.getId());
        log.info("조 조회 완료: {}개 조", groups.size());

        List<BracketResponse.GroupInfo> groupInfos = new ArrayList<>();
        boolean allGroupsCompleted = true;
        boolean anyGroupHasTie = false;

        for (TournamentGroup group : groups) {
            // 해당 조의 경기들
            List<TournamentMatch> groupMatches = matches.stream()
                    .filter(m -> group.getGroupName().equals(m.getGroupId()))
                    .collect(Collectors.toList());

            // 순위표 계산
            List<BracketResponse.TeamStanding> standings = calculateStandings(group, groupMatches);

            // 조별리그 완료 여부 (모든 경기가 종료됨)
            boolean groupCompleted = groupMatches.stream().allMatch(TournamentMatch::isFinished);
            if (!groupCompleted) {
                allGroupsCompleted = false;
            }

            // 동점 체크 (2위와 3위가 동점인 경우) - 진출권 경쟁
            boolean hasTie = false;
            List<Long> tiedTeamIds = new ArrayList<>();
            if (groupCompleted && standings.size() >= 3) {
                int secondPlacePoints = standings.get(1).getPoints();
                int thirdPlacePoints = standings.get(2).getPoints();
                if (secondPlacePoints == thirdPlacePoints) {
                    hasTie = true;
                    anyGroupHasTie = true;
                    // 동점인 모든 팀 찾기
                    for (BracketResponse.TeamStanding standing : standings) {
                        if (standing.getPoints() == secondPlacePoints) {
                            tiedTeamIds.add(standing.getTeamId());
                        }
                    }
                }
            }

            BracketResponse.GroupInfo groupInfo = BracketResponse.GroupInfo.builder()
                    .groupName(group.getGroupName())
                    .standings(standings)
                    .matches(groupMatches.stream()
                            .map(MatchResponse::from)
                            .collect(Collectors.toList()))
                    .groupCompleted(groupCompleted)
                    .hasTie(hasTie)
                    .tiedTeamIds(tiedTeamIds)
                    .build();

            groupInfos.add(groupInfo);
        }

        // 결선 토너먼트 경기들
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

        // 결선 경기가 있고, 팀이 배정되어 있는지 확인
        boolean knockoutTeamsAssigned = knockoutMatches.stream()
                .anyMatch(m -> m.getTeam1() != null || m.getTeam2() != null);

        return builder
                .groups(groupInfos)
                .rounds(rounds)
                .groupStageCompleted(allGroupsCompleted)
                .needsQualifierSelection(allGroupsCompleted && anyGroupHasTie && !knockoutTeamsAssigned)
                .knockoutGenerated(knockoutTeamsAssigned)
                .build();
    }

    /**
     * 스위스 시스템 대진표 구성
     */
    private BracketResponse buildSwissSystemBracket(
            BracketResponse.BracketResponseBuilder builder,
            List<TournamentMatch> matches) {
        
        Map<Integer, List<TournamentMatch>> matchesByRound = matches.stream()
                .collect(Collectors.groupingBy(TournamentMatch::getRound));

        Integer maxRound = matchesByRound.keySet().stream().max(Integer::compareTo).orElse(0);
        
        List<BracketResponse.RoundMatches> rounds = new ArrayList<>();
        for (int round = 1; round <= maxRound; round++) {
            List<TournamentMatch> roundMatches = matchesByRound.getOrDefault(round, Collections.emptyList());
            
            BracketResponse.RoundMatches roundData = BracketResponse.RoundMatches.builder()
                    .round(round)
                    .roundName("라운드 " + round)
                    .matches(roundMatches.stream()
                            .map(MatchResponse::from)
                            .collect(Collectors.toList()))
                    .build();
            
            rounds.add(roundData);
        }

        return builder
                .totalRounds(maxRound)
                .currentRound(getCurrentRound(matchesByRound))
                .rounds(rounds)
                .build();
    }

    /**
     * 라운드명 생성 (16강, 8강, 준결승, 결승)
     */
    private String getRoundName(int round, int totalRounds) {
        int teamsInRound = (int) Math.pow(2, totalRounds - round + 1);
        
        if (round == totalRounds) {
            return "결승";
        } else if (round == totalRounds - 1) {
            return "준결승";
        } else if (round == totalRounds - 2) {
            return "준준결승";
        } else {
            return teamsInRound + "강";
        }
    }

    private String getRoundDisplayName(int round, int totalRounds, List<TournamentMatch> roundMatches) {
        String baseName = getRoundName(round, totalRounds);
        if (round == totalRounds && roundMatches.size() >= 2) {
            return "결승 / 3·4위전";
        }
        return baseName;
    }

    private List<MatchResponse> toMatchResponses(
            List<TournamentMatch> matches,
            int round,
            int totalRounds
    ) {
        List<TournamentMatch> sortedMatches = matches.stream()
                .sorted(Comparator.comparing(TournamentMatch::getMatchNumber))
                .collect(Collectors.toList());

        List<MatchResponse> responses = sortedMatches.stream()
                .map(MatchResponse::from)
                .collect(Collectors.toList());

        if (round == totalRounds && sortedMatches.size() >= 2) {
            for (int i = 0; i < responses.size(); i++) {
                TournamentMatch match = sortedMatches.get(i);
                MatchResponse response = responses.get(i);
                if (match.getMatchNumber() == 1) {
                    response.setMatchLabel("결승");
                } else if (match.getMatchNumber() == 2) {
                    response.setMatchLabel("3·4위전");
                }
            }
        }

        return responses;
    }

    /**
     * 현재 진행 중인 라운드 계산
     */
    private Integer getCurrentRound(Map<Integer, List<TournamentMatch>> matchesByRound) {
        List<Integer> rounds = new ArrayList<>(matchesByRound.keySet());
        Collections.sort(rounds);
        for (Integer round : rounds) {
            boolean hasUnfinished = matchesByRound.get(round).stream()
                    .anyMatch(m -> m.getStatus() != TournamentMatch.MatchStatus.FINISHED);
            if (hasUnfinished) {
                return round;
            }
        }
        return rounds.isEmpty() ? 1 : rounds.get(rounds.size() - 1);
    }

    /**
     * 조별 순위표 계산
     */
    private List<BracketResponse.TeamStanding> calculateStandings(
            TournamentGroup group,
            List<TournamentMatch> matches) {
        
        Map<Long, BracketResponse.TeamStanding> standingsMap = new HashMap<>();

        // 초기화
        group.getTeams().forEach(team -> {
            standingsMap.put(team.getId(), BracketResponse.TeamStanding.builder()
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .teamLogoUrl(team.getLogoUrl())
                    .played(0)
                    .won(0)
                    .drawn(0)
                    .lost(0)
                    .goalsFor(0)
                    .goalsAgainst(0)
                    .goalDifference(0)
                    .points(0)
                    .build());
        });

        // 경기 결과 집계
        matches.stream()
                .filter(TournamentMatch::isFinished)
                .forEach(match -> {
                    if (match.getTeam1() == null || match.getTeam2() == null) {
                        return;
                    }
                    if (match.getTeam1Score() == null || match.getTeam2Score() == null) {
                        return;
                    }
                    Long team1Id = match.getTeam1().getId();
                    Long team2Id = match.getTeam2().getId();
                    
                    BracketResponse.TeamStanding team1Standing = standingsMap.get(team1Id);
                    BracketResponse.TeamStanding team2Standing = standingsMap.get(team2Id);
                    if (team1Standing == null || team2Standing == null) {
                        return;
                    }

                    team1Standing.setPlayed(team1Standing.getPlayed() + 1);
                    team2Standing.setPlayed(team2Standing.getPlayed() + 1);

                    team1Standing.setGoalsFor(team1Standing.getGoalsFor() + match.getTeam1Score());
                    team1Standing.setGoalsAgainst(team1Standing.getGoalsAgainst() + match.getTeam2Score());
                    
                    team2Standing.setGoalsFor(team2Standing.getGoalsFor() + match.getTeam2Score());
                    team2Standing.setGoalsAgainst(team2Standing.getGoalsAgainst() + match.getTeam1Score());

                    if (match.getWinner() == null) {
                        // 무승부
                        team1Standing.setDrawn(team1Standing.getDrawn() + 1);
                        team2Standing.setDrawn(team2Standing.getDrawn() + 1);
                        team1Standing.setPoints(team1Standing.getPoints() + 1);
                        team2Standing.setPoints(team2Standing.getPoints() + 1);
                    } else if (match.getWinner().getId().equals(team1Id)) {
                        // Team1 승
                        team1Standing.setWon(team1Standing.getWon() + 1);
                        team2Standing.setLost(team2Standing.getLost() + 1);
                        team1Standing.setPoints(team1Standing.getPoints() + 3);
                    } else {
                        // Team2 승
                        team2Standing.setWon(team2Standing.getWon() + 1);
                        team1Standing.setLost(team1Standing.getLost() + 1);
                        team2Standing.setPoints(team2Standing.getPoints() + 3);
                    }

                    team1Standing.setGoalDifference(team1Standing.getGoalsFor() - team1Standing.getGoalsAgainst());
                    team2Standing.setGoalDifference(team2Standing.getGoalsFor() - team2Standing.getGoalsAgainst());
                });

        // 순위 정렬 (승점 > 골득실 > 다득점)
        List<BracketResponse.TeamStanding> sortedStandings = standingsMap.values().stream()
                .sorted(Comparator
                        .comparing(BracketResponse.TeamStanding::getPoints).reversed()
                        .thenComparing(BracketResponse.TeamStanding::getGoalDifference).reversed()
                        .thenComparing(BracketResponse.TeamStanding::getGoalsFor).reversed())
                .collect(Collectors.toList());

        // 순위 할당
        for (int i = 0; i < sortedStandings.size(); i++) {
            sortedStandings.get(i).setRank(i + 1);
        }

        return sortedStandings;
    }

    /**
     * 경기 일정 업데이트
     */
    @Transactional
    public MatchResponse updateMatchSchedule(Long tournamentId, Long matchId, MatchScheduleRequest request) {
        TournamentMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("경기를 찾을 수 없습니다: " + matchId));

        if (!match.getTournament().getId().equals(tournamentId)) {
            throw new RuntimeException("대회 정보가 일치하지 않습니다.");
        }

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
    public List<MatchResponse> updateMatchSchedules(Long tournamentId, BatchMatchScheduleRequest request) {
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
                .collect(Collectors.toMap(TournamentMatch::getId, match -> match));

        List<TournamentMatch> updatedMatches = new ArrayList<>();
        for (MatchScheduleUpdateRequest schedule : request.getSchedules()) {
            TournamentMatch match = matchesById.get(schedule.getMatchId());
            if (match == null) {
                throw new RuntimeException("경기를 찾을 수 없습니다: " + schedule.getMatchId());
            }

            if (!match.getTournament().getId().equals(tournamentId)) {
                throw new RuntimeException("대회 정보가 일치하지 않습니다.");
            }

            match.updateSchedule(schedule.getScheduledAt(), schedule.getVenueName());
            updatedMatches.add(match);
        }

        List<TournamentMatch> savedMatches = matchRepository.saveAll(updatedMatches);
        log.info("경기 일정 일괄 업데이트: tournamentId={}, matchCount={}", tournamentId, savedMatches.size());

        return savedMatches.stream()
                .map(MatchResponse::from)
                .toList();
    }

    /**
     * 경기 결과 입력
     */
    @Transactional
    public MatchResponse recordMatchResult(Long tournamentId, Long matchId, MatchResultRequest request) {
        TournamentMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("경기를 찾을 수 없습니다: " + matchId));

        if (!match.getTournament().getId().equals(tournamentId)) {
            throw new RuntimeException("대회 정보가 일치하지 않습니다.");
        }

        boolean isKnockoutMatch = match.getTournament().getTournamentType() == TournamentType.SINGLE_ELIMINATION
                || (match.getTournament().getTournamentType() == TournamentType.GROUP_STAGE && match.getGroupId() == null);

        boolean isDrawInRegularTime = request.getTeam1Score() != null
                && request.getTeam2Score() != null
                && request.getTeam1Score().equals(request.getTeam2Score());

        if (isKnockoutMatch && isDrawInRegularTime) {
            if (request.getTeam1PenaltyScore() == null || request.getTeam2PenaltyScore() == null) {
                throw new RuntimeException("결선 토너먼트 동점 경기는 승부차기 점수를 입력해야 합니다.");
            }
            if (request.getTeam1PenaltyScore().equals(request.getTeam2PenaltyScore())) {
                throw new RuntimeException("승부차기 점수는 동점일 수 없습니다.");
            }
        }

        if (!isKnockoutMatch && (request.getTeam1PenaltyScore() != null || request.getTeam2PenaltyScore() != null)) {
            throw new RuntimeException("조별리그 경기에는 승부차기 점수를 입력할 수 없습니다.");
        }

        match.recordResult(
                request.getTeam1Score(),
                request.getTeam2Score(),
                request.getTeam1PenaltyScore(),
                request.getTeam2PenaltyScore()
        );

        TournamentMatch saved = matchRepository.save(match);

        // 다음 라운드 진출 처리
        Tournament tournament = saved.getTournament();
        if (tournament.getTournamentType() == TournamentType.SINGLE_ELIMINATION) {
            advanceWinnerToNextRound(saved);
        } else if (tournament.getTournamentType() == TournamentType.GROUP_STAGE) {
            if (saved.getGroupId() != null) {
                // 조별리그 경기 -> 조별리그 완료 시 결선 토너먼트 생성
                checkAndGenerateKnockoutBracket(tournament);
            } else {
                // 결선 토너먼트 경기 -> 다음 라운드 진출 처리
                advanceWinnerToNextRound(saved);
            }
        }

        return MatchResponse.from(saved);
    }

    /**
     * 승자를 다음 라운드로 진출시킴
     */
    private void advanceWinnerToNextRound(TournamentMatch match) {
        if (match.getWinner() == null) {
            log.warn("무승부 경기는 진출 처리할 수 없습니다: matchId={}", match.getId());
            return;
        }

        int nextRound = match.getRound() + 1;
        int nextMatchNumber = (match.getMatchNumber() + 1) / 2;

        List<TournamentMatch> nextMatches = matchRepository.findByTournamentIdAndRound(
                match.getTournament().getId(), nextRound);

        TournamentMatch nextMatch = nextMatches.stream()
                .filter(m -> m.getMatchNumber() == nextMatchNumber)
                .findFirst()
                .orElse(null);

        if (nextMatch != null) {
            // 홀수 매치는 team1로, 짝수 매치는 team2로 진출
            if (match.getMatchNumber() % 2 == 1) {
                nextMatch.assignTeam1(match.getWinner());
            } else {
                nextMatch.assignTeam2(match.getWinner());
            }
            matchRepository.save(nextMatch);
            log.info("승자 진출 처리: {}팀 -> {}라운드 {}경기",
                    match.getWinner().getName(), nextRound, nextMatchNumber);
        }

        assignLoserToThirdPlaceMatch(match, nextRound);
    }

    private void assignLoserToThirdPlaceMatch(TournamentMatch match, int nextRound) {
        Team loser = match.getLoser();
        if (loser == null) {
            return;
        }

        List<TournamentMatch> currentRoundMatches = matchRepository.findByTournamentIdAndRound(
                match.getTournament().getId(), match.getRound());
        if (currentRoundMatches.size() != 2) {
            return;
        }

        List<TournamentMatch> nextRoundMatches = matchRepository.findByTournamentIdAndRound(
                match.getTournament().getId(), nextRound);
        if (nextRoundMatches.size() < 2) {
            return;
        }

        TournamentMatch thirdPlaceMatch = nextRoundMatches.stream()
                .filter(m -> m.getMatchNumber() == 2)
                .findFirst()
                .orElse(null);
        if (thirdPlaceMatch == null) {
            return;
        }

        if (match.getMatchNumber() % 2 == 1) {
            thirdPlaceMatch.assignTeam1(loser);
        } else {
            thirdPlaceMatch.assignTeam2(loser);
        }

        matchRepository.save(thirdPlaceMatch);
        log.info("패자 3,4위전 배치: {}팀 -> {}라운드 2경기",
                loser.getName(), nextRound);
    }

    /**
     * 조별리그 완료 여부 확인 후 결선 토너먼트 팀 배정
     * 동점 시에는 자동 배정하지 않고 개최자가 수동으로 선택하도록 함
     */
    private void checkAndGenerateKnockoutBracket(Tournament tournament) {
        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournament.getId());

        // 결선 경기에 이미 팀이 배정되어 있으면 스킵
        List<TournamentMatch> knockoutMatches = allMatches.stream()
                .filter(m -> m.getGroupId() == null && m.getRound() > 1)
                .collect(Collectors.toList());

        boolean knockoutTeamsAssigned = knockoutMatches.stream()
                .anyMatch(m -> m.getTeam1() != null || m.getTeam2() != null);
        if (knockoutTeamsAssigned) {
            return;
        }

        // 조별리그 경기 완료 여부 확인
        List<TournamentMatch> groupMatches = allMatches.stream()
                .filter(m -> m.getGroupId() != null)
                .collect(Collectors.toList());

        boolean allGroupMatchesFinished = groupMatches.stream()
                .allMatch(m -> m.getStatus() == TournamentMatch.MatchStatus.FINISHED);

        if (!allGroupMatchesFinished) {
            return;
        }

        // 각 조별 순위 계산 및 동점 체크
        List<TournamentGroup> groups = groupRepository.findByTournamentIdWithTeams(tournament.getId());

        for (TournamentGroup group : groups) {
            List<TournamentMatch> groupMatchList = groupMatches.stream()
                    .filter(m -> group.getGroupName().equals(m.getGroupId()))
                    .collect(Collectors.toList());

            List<BracketResponse.TeamStanding> standings = calculateStandings(group, groupMatchList);

            // 동점 체크: 2위와 3위가 같은 승점이면 수동 선택 필요
            if (standings.size() >= 3) {
                int secondPlacePoints = standings.get(1).getPoints();
                int thirdPlacePoints = standings.get(2).getPoints();
                if (secondPlacePoints == thirdPlacePoints) {
                    log.info("조별리그 {} 동점 발견: 2위와 3위가 승점 {}으로 동률. 수동 선택 필요",
                            group.getGroupName(), secondPlacePoints);
                    // 동점이 있으면 자동 생성하지 않음
                    return;
                }
            }
        }

        log.info("조별리그 완료, 동점 없음. 결선 토너먼트 팀 배정 시작: tournamentId={}", tournament.getId());

        // 각 조별 진출팀 선정
        List<com.futsal.team.domain.Team> qualifiedTeams = new ArrayList<>();

        for (TournamentGroup group : groups) {
            List<TournamentMatch> groupMatchList = groupMatches.stream()
                    .filter(m -> group.getGroupName().equals(m.getGroupId()))
                    .collect(Collectors.toList());

            List<BracketResponse.TeamStanding> standings = calculateStandings(group, groupMatchList);

            // 상위 N팀 진출 (조당)
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

        // 결선 토너먼트 매치에 팀 배정
        assignTeamsToKnockoutMatches(tournament, qualifiedTeams, knockoutMatches, groups.size());
    }

    /**
     * 결선 토너먼트 매치에 팀 배정 (이미 생성된 빈 매치에 팀 할당)
     */
    private void assignTeamsToKnockoutMatches(Tournament tournament,
                                              List<com.futsal.team.domain.Team> qualifiedTeams,
                                              List<TournamentMatch> knockoutMatches,
                                              int groupCount) {
        int teamCount = qualifiedTeams.size();
        if (teamCount < 2) {
            log.warn("결선 진출팀이 2팀 미만입니다: {}", teamCount);
            return;
        }

        // 2의 거듭제곱으로 올림
        int bracketSize = 1;
        while (bracketSize < teamCount) {
            bracketSize *= 2;
        }

        log.info("결선 토너먼트 팀 배정: {}팀", teamCount);

        // 시드 배치 (A조 1위 vs B조 2위, B조 1위 vs A조 2위 등)
        List<com.futsal.team.domain.Team> seededTeams = new ArrayList<>();
        int teamsPerGroup = teamCount / groupCount;

        // 크로스 시드 배치
        for (int i = 0; i < teamsPerGroup; i++) {
            for (int g = 0; g < groupCount; g++) {
                int teamIndex = g * teamsPerGroup + i;
                if (teamIndex < qualifiedTeams.size()) {
                    seededTeams.add(qualifiedTeams.get(teamIndex));
                }
            }
        }

        // 결선 1라운드 매치 찾기 (round = 2)
        int knockoutRound = 2;
        List<TournamentMatch> firstRoundMatches = knockoutMatches.stream()
                .filter(m -> m.getRound() == knockoutRound)
                .sorted(Comparator.comparing(TournamentMatch::getMatchNumber))
                .collect(Collectors.toList());

        // 1라운드 매치에 팀 배정 (크로스 매칭)
        for (int i = 0; i < firstRoundMatches.size(); i++) {
            TournamentMatch match = firstRoundMatches.get(i);

            int team1Index = i;
            int team2Index = bracketSize - 1 - i;

            if (team1Index < seededTeams.size()) {
                match.assignTeam1(seededTeams.get(team1Index));
            }
            if (team2Index < seededTeams.size()) {
                match.assignTeam2(seededTeams.get(team2Index));
            }

            matchRepository.save(match);
        }

        log.info("결선 토너먼트 팀 배정 완료: {}경기", firstRoundMatches.size());
    }

    /**
     * 대진표 이미지 업로드 (수동 대진표)
     * - 기존 자동 생성 대진표가 있으면 삭제
     * - 이미지 업로드 후 MANUAL 모드로 전환
     */
    @Transactional
    public BracketResponse uploadBracketImages(Long tournamentId, List<MultipartFile> files, User user) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대진표를 수정할 권한이 없습니다.");
        }

        // 기존 자동 생성 대진표 데이터 삭제
        if (!tournament.isManualBracket() && Boolean.TRUE.equals(tournament.getBracketGenerated())) {
            log.info("기존 자동 생성 대진표 삭제: tournamentId={}", tournamentId);
            matchRepository.deleteByTournamentId(tournamentId);
        }

        // 이미지 업로드
        List<String> imageUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            String imageUrl = s3Service.uploadBracketImage(file);
            imageUrls.add(imageUrl);
        }

        // MANUAL 모드로 전환
        tournament.switchToManualBracket(imageUrls);
        tournamentRepository.save(tournament);

        log.info("대진표 이미지 업로드 완료: tournamentId={}, imageCount={}", tournamentId, imageUrls.size());

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
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대진표를 수정할 권한이 없습니다.");
        }

        // AUTO 모드로 전환
        tournament.switchToAutoBracket();
        tournamentRepository.save(tournament);

        log.info("대진표 이미지 삭제 완료, AUTO 모드로 전환: tournamentId={}", tournamentId);
    }

    /**
     * 조별리그 진출팀 수동 선택 후 결선 토너먼트 생성
     * 동점 상황에서 개최자가 직접 진출팀을 선택할 때 사용
     */
    @Transactional
    public BracketResponse selectQualifiersAndGenerateKnockout(
            Long tournamentId,
            QualifierSelectionRequest request,
            User user) {

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대진표를 수정할 권한이 없습니다.");
        }

        return selectQualifiersAndGenerateKnockoutInternal(tournament, request);
    }

    @Transactional
    public BracketResponse selectQualifiersAndGenerateKnockoutByShareCode(
            Long tournamentId,
            QualifierSelectionRequest request) {

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        return selectQualifiersAndGenerateKnockoutInternal(tournament, request);
    }

    private BracketResponse selectQualifiersAndGenerateKnockoutInternal(
            Tournament tournament,
            QualifierSelectionRequest request) {
        Long tournamentId = tournament.getId();

        // 조별리그 대회인지 확인
        if (tournament.getTournamentType() != TournamentType.GROUP_STAGE) {
            throw new RuntimeException("조별리그 대회에서만 사용할 수 있습니다.");
        }

        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournamentId);

        // 결선 경기 조회
        List<TournamentMatch> knockoutMatches = allMatches.stream()
                .filter(m -> m.getGroupId() == null && m.getRound() > 1)
                .collect(Collectors.toList());

        // 결선 경기에 이미 팀이 배정되어 있는지 확인
        boolean knockoutTeamsAssigned = knockoutMatches.stream()
                .anyMatch(m -> m.getTeam1() != null || m.getTeam2() != null);
        if (knockoutTeamsAssigned) {
            throw new RuntimeException("이미 결선 토너먼트 팀이 배정되었습니다.");
        }

        // 조별리그 경기 완료 여부 확인
        List<TournamentMatch> groupMatches = allMatches.stream()
                .filter(m -> m.getGroupId() != null)
                .collect(Collectors.toList());
        boolean allGroupMatchesFinished = groupMatches.stream()
                .allMatch(m -> m.getStatus() == TournamentMatch.MatchStatus.FINISHED);
        if (!allGroupMatchesFinished) {
            throw new RuntimeException("모든 조별리그 경기가 종료되어야 합니다.");
        }

        // 조 정보 조회
        List<TournamentGroup> groups = groupRepository.findByTournamentIdWithTeams(tournamentId);
        if (request.getQualifiedTeamsByGroup() == null || request.getQualifiedTeamsByGroup().isEmpty()) {
            throw new RuntimeException("각 조별 진출팀을 선택해주세요.");
        }

        // 각 조별로 선택된 팀 검증 및 수집
        List<Team> qualifiedTeams = new ArrayList<>();
        for (TournamentGroup group : groups) {
            String groupName = group.getGroupName();
            List<Long> selectedTeamIds = request.getQualifiedTeamsByGroup().get(groupName);

            if (selectedTeamIds == null || selectedTeamIds.isEmpty()) {
                throw new RuntimeException("조 " + groupName + "의 진출팀을 선택해주세요.");
            }

            // 선택된 팀이 해당 조에 속하는지 확인
            Set<Long> groupTeamIds = group.getTeams().stream()
                    .map(Team::getId)
                    .collect(Collectors.toSet());

            for (Long teamId : selectedTeamIds) {
                if (!groupTeamIds.contains(teamId)) {
                    throw new RuntimeException("팀 ID " + teamId + "는 조 " + groupName + "에 속하지 않습니다.");
                }
                Team team = teamRepository.findById(teamId)
                        .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
                qualifiedTeams.add(team);
            }
        }

        log.info("수동 선택된 결선 진출팀: {}팀", qualifiedTeams.size());

        // 결선 토너먼트 매치에 팀 배정
        assignTeamsToKnockoutMatches(tournament, qualifiedTeams, knockoutMatches, groups.size());

        // 업데이트된 대진표 반환
        return getBracket(tournamentId);
    }

    /**
     * 조별리그 결선 1라운드 팀 수동 재배치
     */
    @Transactional
    public BracketResponse updateKnockoutAssignments(
            Long tournamentId,
            KnockoutMatchAssignmentRequest request,
            User user
    ) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대진표를 수정할 권한이 없습니다.");
        }

        if (tournament.getTournamentType() != TournamentType.GROUP_STAGE) {
            throw new RuntimeException("조별리그 결선 토너먼트에서만 사용할 수 있습니다.");
        }

        if (!Boolean.TRUE.equals(tournament.getBracketGenerated()) || tournament.isManualBracket()) {
            throw new RuntimeException("자동 생성된 대진표에서만 사용할 수 있습니다.");
        }

        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournamentId);
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

        boolean hasUnassignedSlot = firstRoundMatches.stream()
                .anyMatch(m -> m.getTeam1() == null || m.getTeam2() == null);
        if (hasUnassignedSlot) {
            throw new RuntimeException("아직 팀 배정이 완료되지 않은 결선 경기입니다.");
        }

        if (request == null || request.getAssignments() == null || request.getAssignments().isEmpty()) {
            throw new RuntimeException("수정할 결선 대진 정보가 없습니다.");
        }

        Map<Long, TournamentMatch> firstRoundMatchMap = firstRoundMatches.stream()
                .collect(Collectors.toMap(TournamentMatch::getId, match -> match));

        if (request.getAssignments().size() != firstRoundMatches.size()) {
            throw new RuntimeException("결선 1라운드 경기 수와 요청 데이터가 일치하지 않습니다.");
        }

        Set<Long> currentTeamIds = firstRoundMatches.stream()
                .flatMap(match -> java.util.stream.Stream.of(match.getTeam1(), match.getTeam2()))
                .map(Team::getId)
                .collect(Collectors.toSet());

        Set<Long> requestedMatchIds = new HashSet<>();
        Set<Long> requestedTeamIds = new HashSet<>();

        for (KnockoutMatchAssignmentRequest.MatchAssignment assignment : request.getAssignments()) {
            if (assignment.getMatchId() == null) {
                throw new RuntimeException("경기 ID가 누락되었습니다.");
            }
            if (!requestedMatchIds.add(assignment.getMatchId())) {
                throw new RuntimeException("같은 경기가 중복 요청되었습니다.");
            }

            TournamentMatch match = firstRoundMatchMap.get(assignment.getMatchId());
            if (match == null) {
                throw new RuntimeException("결선 1라운드가 아닌 경기가 포함되어 있습니다.");
            }

            Long team1Id = assignment.getTeam1Id();
            Long team2Id = assignment.getTeam2Id();

            if (team1Id == null || team2Id == null) {
                throw new RuntimeException("각 경기에는 양 팀이 모두 지정되어야 합니다.");
            }
            if (team1Id.equals(team2Id)) {
                throw new RuntimeException("한 경기에는 같은 팀을 중복 배정할 수 없습니다.");
            }

            requestedTeamIds.add(team1Id);
            requestedTeamIds.add(team2Id);
        }

        if (!requestedMatchIds.equals(firstRoundMatchMap.keySet())) {
            throw new RuntimeException("결선 1라운드 전체 경기에 대한 배정 정보가 필요합니다.");
        }

        if (!requestedTeamIds.equals(currentTeamIds)) {
            throw new RuntimeException("현재 결선 진출팀 범위 안에서만 대진을 재배치할 수 있습니다.");
        }

        Map<Long, Team> teamMap = teamRepository.findAllById(currentTeamIds).stream()
                .collect(Collectors.toMap(Team::getId, team -> team));

        for (KnockoutMatchAssignmentRequest.MatchAssignment assignment : request.getAssignments()) {
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

        return getBracket(tournamentId);
    }
}
