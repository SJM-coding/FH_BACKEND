package com.futsal.tournament.service;

import com.futsal.common.storage.S3Service;
import com.futsal.team.domain.Team;
import com.futsal.team.repository.TeamRepository;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.dto.*;
import com.futsal.tournament.repository.TournamentGroupRepository;
import com.futsal.tournament.repository.TournamentMatchRepository;
import com.futsal.tournament.repository.TournamentRepository;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final TeamRepository teamRepository;
    private final S3Service s3Service;

    /**
     * 대진표 전체 조회
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "bracket", key = "#tournamentId")
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

        boolean knockoutGenerated = !knockoutMatches.isEmpty();

        return builder
                .groups(groupInfos)
                .rounds(rounds)
                .groupStageCompleted(allGroupsCompleted)
                .needsQualifierSelection(allGroupsCompleted && anyGroupHasTie && !knockoutGenerated)
                .knockoutGenerated(knockoutGenerated)
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
     * 경기 일정 업데이트
     */
    @Transactional
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
    public MatchResponse updateMatchSchedule(Long tournamentId, Long matchId, MatchScheduleRequest request) {
        TournamentMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("경기를 찾을 수 없습니다: " + matchId));

        if (!match.getTournament().getId().equals(tournamentId)) {
            throw new RuntimeException("대회 정보가 일치하지 않습니다.");
        }

        match.updateSchedule(request.getScheduledAt());

        TournamentMatch saved = matchRepository.save(match);
        log.info("경기 일정 업데이트: matchId={}, scheduledAt={}", matchId, request.getScheduledAt());

        return MatchResponse.from(saved);
    }

    /**
     * 경기 일정 일괄 업데이트
     */
    @Transactional
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
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

            match.updateSchedule(schedule.getScheduledAt());
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
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
    public MatchResponse recordMatchResult(Long tournamentId, Long matchId, MatchResultRequest request) {
        TournamentMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("경기를 찾을 수 없습니다: " + matchId));

        if (!match.getTournament().getId().equals(tournamentId)) {
            throw new RuntimeException("대회 정보가 일치하지 않습니다.");
        }

        match.recordResult(
                request.getTeam1Score(),
                request.getTeam2Score()
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
    }

    /**
     * 조별리그 완료 여부 확인 후 결선 토너먼트 생성
     * 동점 시에는 자동 생성하지 않고 개최자가 수동으로 선택하도록 함
     */
    private void checkAndGenerateKnockoutBracket(Tournament tournament) {
        // 이미 결선 경기가 있으면 스킵
        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournament.getId());
        boolean hasKnockoutMatches = allMatches.stream()
                .anyMatch(m -> m.getGroupId() == null && m.getRound() > 1);
        if (hasKnockoutMatches) {
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

        log.info("조별리그 완료, 동점 없음. 결선 토너먼트 자동 생성 시작: tournamentId={}", tournament.getId());

        // 각 조별 진출팀 선정
        List<com.futsal.team.domain.Team> qualifiedTeams = new ArrayList<>();

        for (TournamentGroup group : groups) {
            List<TournamentMatch> groupMatchList = groupMatches.stream()
                    .filter(m -> group.getGroupName().equals(m.getGroupId()))
                    .collect(Collectors.toList());

            List<BracketResponse.TeamStanding> standings = calculateStandings(group, groupMatchList);

            // 상위 2팀 진출 (조당)
            int advanceCount = Math.min(2, standings.size());
            for (int i = 0; i < advanceCount; i++) {
                Long teamId = standings.get(i).getTeamId();
                group.getTeams().stream()
                        .filter(t -> t.getId().equals(teamId))
                        .findFirst()
                        .ifPresent(qualifiedTeams::add);
            }
        }

        log.info("결선 진출팀 {}팀 선정 완료", qualifiedTeams.size());

        // 결선 토너먼트 매치 생성
        generateKnockoutMatches(tournament, qualifiedTeams, groups.size());
    }

    /**
     * 결선 토너먼트 매치 생성
     */
    private void generateKnockoutMatches(Tournament tournament,
                                         List<com.futsal.team.domain.Team> qualifiedTeams,
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

        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));
        log.info("결선 토너먼트 생성: {}팀, {}라운드", teamCount, totalRounds);

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

        // 1라운드 (결선) 매치 생성
        int matchNumber = 1;
        int knockoutRound = 2; // 조별리그가 round 1
        int matchesInFirstRound = bracketSize / 2;

        for (int i = 0; i < matchesInFirstRound; i++) {
            TournamentMatch match = TournamentMatch.builder()
                    .tournament(tournament)
                    .round(knockoutRound)
                    .matchNumber(matchNumber++)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build();

            // 팀 배정 (크로스 매칭: i번째 vs (bracketSize-1-i)번째)
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

        // 이후 라운드 빈 매치 생성
        int currentMatchCount = matchesInFirstRound;
        for (int round = knockoutRound + 1; round <= knockoutRound + totalRounds - 1; round++) {
            currentMatchCount /= 2;
            for (int i = 1; i <= currentMatchCount; i++) {
                TournamentMatch match = TournamentMatch.builder()
                        .tournament(tournament)
                        .round(round)
                        .matchNumber(i)
                        .status(TournamentMatch.MatchStatus.SCHEDULED)
                        .build();
                matchRepository.save(match);
            }
        }

        log.info("결선 토너먼트 생성 완료: {}경기", matchNumber - 1);
    }

    /**
     * 대진표 이미지 업로드 (수동 대진표)
     * - 기존 자동 생성 대진표가 있으면 삭제
     * - 이미지 업로드 후 MANUAL 모드로 전환
     */
    @Transactional
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
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
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
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
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
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
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
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

        // 이미 결선 경기가 있는지 확인
        List<TournamentMatch> allMatches = matchRepository.findByTournamentIdWithTeams(tournamentId);
        boolean hasKnockoutMatches = allMatches.stream()
                .anyMatch(m -> m.getGroupId() == null && m.getRound() > 1);
        if (hasKnockoutMatches) {
            throw new RuntimeException("이미 결선 토너먼트가 생성되었습니다.");
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

        // 결선 토너먼트 매치 생성
        generateKnockoutMatches(tournament, qualifiedTeams, groups.size());

        // 업데이트된 대진표 반환
        return getBracket(tournamentId);
    }
}
