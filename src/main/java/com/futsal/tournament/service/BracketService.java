package com.futsal.tournament.service;

import com.futsal.tournament.domain.*;
import com.futsal.tournament.dto.*;
import com.futsal.tournament.repository.TournamentGroupRepository;
import com.futsal.tournament.repository.TournamentMatchRepository;
import com.futsal.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 대진표 조회 및 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketService {

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentGroupRepository groupRepository;

    /**
     * 대진표 전체 조회
     */
    @Transactional(readOnly = true)
    public BracketResponse getBracket(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        if (!tournament.getBracketGenerated()) {
            throw new RuntimeException("아직 대진표가 생성되지 않았습니다.");
        }

        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournamentId);
        
        BracketResponse.BracketResponseBuilder builder = BracketResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentTitle(tournament.getTitle())
                .tournamentType(tournament.getTournamentType().name())
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
                    .roundName(getRoundName(round, maxRound))
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
     * 조별리그 대진표 구성
     */
    private BracketResponse buildGroupStageBracket(
            BracketResponse.BracketResponseBuilder builder,
            Tournament tournament,
            List<TournamentMatch> matches) {
        
        List<TournamentGroup> groups = groupRepository.findByTournamentIdWithTeams(tournament.getId());
        
        List<BracketResponse.GroupInfo> groupInfos = new ArrayList<>();
        for (TournamentGroup group : groups) {
            // 해당 조의 경기들
            List<TournamentMatch> groupMatches = matches.stream()
                    .filter(m -> group.getGroupName().equals(m.getGroupId()))
                    .collect(Collectors.toList());

            // 순위표 계산
            List<BracketResponse.TeamStanding> standings = calculateStandings(group, groupMatches);

            BracketResponse.GroupInfo groupInfo = BracketResponse.GroupInfo.builder()
                    .groupName(group.getGroupName())
                    .standings(standings)
                    .matches(groupMatches.stream()
                            .map(MatchResponse::from)
                            .collect(Collectors.toList()))
                    .build();

            groupInfos.add(groupInfo);
        }

        // 결선 토너먼트 경기들
        List<TournamentMatch> knockoutMatches = matches.stream()
                .filter(m -> m.getGroupId() == null)
                .collect(Collectors.toList());

        Map<Integer, List<TournamentMatch>> knockoutByRound = knockoutMatches.stream()
                .collect(Collectors.groupingBy(TournamentMatch::getRound));

        List<BracketResponse.RoundMatches> rounds = knockoutByRound.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> BracketResponse.RoundMatches.builder()
                        .round(entry.getKey())
                        .roundName("결선 " + getRoundName(entry.getKey() - 1, knockoutByRound.size()))
                        .matches(entry.getValue().stream()
                                .map(MatchResponse::from)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return builder
                .groups(groupInfos)
                .rounds(rounds)
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
     * 경기 결과 입력
     */
    @Transactional
    public MatchResponse recordMatchResult(Long matchId, MatchResultRequest request) {
        TournamentMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("경기를 찾을 수 없습니다: " + matchId));

        match.recordResult(
                request.getTeam1Score(),
                request.getTeam2Score(),
                request.getTeam1PenaltyScore(),
                request.getTeam2PenaltyScore()
        );

        TournamentMatch saved = matchRepository.save(match);

        // 다음 라운드 진출 처리 (토너먼트인 경우)
        if (saved.getTournament().getTournamentType() == TournamentType.SINGLE_ELIMINATION) {
            advanceWinnerToNextRound(saved);
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
    }
}
