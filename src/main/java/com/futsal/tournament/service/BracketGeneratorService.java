package com.futsal.tournament.service;

import com.futsal.team.domain.Team;
import com.futsal.team.repository.TeamRepository;
import com.futsal.tournament.dto.BracketGenerateRequest;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.repository.TournamentGroupRepository;
import com.futsal.tournament.repository.TournamentMatchRepository;
import com.futsal.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 대진표 생성 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketGeneratorService {

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentGroupRepository groupRepository;
    private final TeamRepository teamRepository;

    /**
     * 대진표 생성 (참가 팀 기반)
     */
    @Transactional
    @CacheEvict(cacheNames = "bracket", key = "#tournamentId")
    public void generateBracket(Long tournamentId, BracketGenerateRequest request) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));
        List<Long> participatingTeamIds = request.getParticipatingTeamIds();

        if (participatingTeamIds == null || participatingTeamIds.isEmpty()) {
            throw new RuntimeException("참가 팀 정보가 없습니다.");
        }

        // 외부 대회는 자동 생성 불가
        if (tournament.getTournamentType() == TournamentType.EXTERNAL) {
            throw new RuntimeException("외부 대회는 이미지 대진표만 등록할 수 있습니다.");
        }

        if (tournament.getBracketGenerated()) {
            throw new RuntimeException("이미 대진표가 생성된 대회입니다.");
        }

        // 팀 검증
        if (participatingTeamIds.size() < tournament.getTournamentType().getMinimumTeams()) {
            throw new RuntimeException(
                String.format("최소 %d팀 이상 필요합니다.",
                    tournament.getTournamentType().getMinimumTeams())
            );
        }

        if (participatingTeamIds.size() > tournament.getMaxTeams()) {
            throw new RuntimeException(
                String.format("최대 %d팀까지만 참가할 수 있습니다.", tournament.getMaxTeams())
            );
        }

        applyGenerationSettings(tournament, request, participatingTeamIds.size());

        // 대진표 생성 타입별 분기
        switch (tournament.getTournamentType()) {
            case SINGLE_ELIMINATION:
                generateSingleEliminationBracket(tournament, participatingTeamIds);
                break;
            case GROUP_STAGE:
                generateGroupStageBracket(tournament, participatingTeamIds);
                break;
            case SWISS_SYSTEM:
                generateSwissSystemBracket(tournament, participatingTeamIds);
                break;
            case EXTERNAL:
                // 위에서 이미 체크했으므로 여기 도달하지 않음
                throw new RuntimeException("외부 대회는 자동 대진표 생성이 불가능합니다.");
        }

        // AUTO 모드로 설정
        tournament.setBracketType(BracketType.AUTO);
        tournament.setBracketGenerated(true);
        tournamentRepository.save(tournament);

        log.info("대진표 생성 완료: 대회 ID={}, 타입={}, 팀 수={}",
            tournamentId, tournament.getTournamentType(), participatingTeamIds.size());
    }

    private void applyGenerationSettings(Tournament tournament, BracketGenerateRequest request, int teamCount) {
        switch (tournament.getTournamentType()) {
            case GROUP_STAGE -> {
                if (request.getGroupCount() != null) {
                    tournament.setGroupCount(request.getGroupCount());
                }
                if (request.getTeamsPerGroup() != null) {
                    tournament.setTeamsPerGroup(request.getTeamsPerGroup());
                }
                if (request.getAdvanceCount() != null) {
                    tournament.setAdvanceCount(request.getAdvanceCount());
                }

                if (tournament.getGroupCount() == null || tournament.getTeamsPerGroup() == null) {
                    throw new RuntimeException("조별리그 설정이 없습니다.");
                }
                if (!Objects.equals(tournament.getGroupCount() * tournament.getTeamsPerGroup(), teamCount)) {
                    throw new RuntimeException(
                            String.format("참가 팀 수(%d)가 조 구성(%d개 조 × %d팀)과 맞지 않습니다.",
                                    teamCount, tournament.getGroupCount(), tournament.getTeamsPerGroup())
                    );
                }
                if (tournament.getAdvanceCount() == null || tournament.getAdvanceCount() < 1) {
                    tournament.setAdvanceCount(2);
                }
            }
            case SWISS_SYSTEM -> {
                if (request.getSwissRounds() != null) {
                    tournament.setSwissRounds(request.getSwissRounds());
                }
                if (tournament.getSwissRounds() == null || tournament.getSwissRounds() < 1) {
                    throw new RuntimeException("스위스 시스템 라운드 수가 올바르지 않습니다.");
                }
            }
            default -> {
            }
        }
    }

    /**
     * 싱글 엘리미네이션 대진표 생성
     */
    private void generateSingleEliminationBracket(Tournament tournament, List<Long> teamIds) {
        int teamCount = teamIds.size();

        // 2의 거듭제곱으로 올림 (부전승 처리)
        int bracketSize = nextPowerOfTwo(teamCount);
        int byeCount = bracketSize - teamCount;

        log.info("토너먼트 대진표 생성: 총 {}팀, 브라켓 크기={}, 부전승={}", teamCount, bracketSize, byeCount);

        // 팀 조회
        Map<Long, Team> teamMap = new HashMap<>();
        for (Long teamId : teamIds) {
            teamRepository.findById(teamId).ifPresent(team -> teamMap.put(teamId, team));
        }

        // 프론트엔드에서 전달받은 순서를 그대로 사용 (사용자가 직접 정렬함)
        List<Long> orderedTeams = new ArrayList<>(teamIds);

        // 부전승 팀 추가 (null로 표시)
        for (int i = 0; i < byeCount; i++) {
            orderedTeams.add(null);
        }

        // 1라운드 매치 생성
        int matchNumber = 1;
        for (int i = 0; i < bracketSize; i += 2) {
            Long team1Id = orderedTeams.get(i);
            Long team2Id = orderedTeams.get(i + 1);

            TournamentMatch match = TournamentMatch.builder()
                    .tournament(tournament)
                    .round(1)
                    .matchNumber(matchNumber++)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build();

            // 팀 배정
            if (team1Id != null && teamMap.containsKey(team1Id)) {
                match.assignTeam1(teamMap.get(team1Id));
            }
            if (team2Id != null && teamMap.containsKey(team2Id)) {
                match.assignTeam2(teamMap.get(team2Id));
            }

            matchRepository.save(match);
        }

        // 이후 라운드 빈 매치 생성 (승자가 올라갈 자리)
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));
        int currentMatchCount = bracketSize / 2;

        for (int round = 2; round <= totalRounds; round++) {
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
    }

    /**
     * 조별 리그 대진표 생성
     */
    private void generateGroupStageBracket(Tournament tournament, List<Long> teamIds) {
        int teamCount = teamIds.size();
        int groupCount = tournament.getGroupCount();
        int teamsPerGroup = tournament.getTeamsPerGroup();

        if (teamCount != groupCount * teamsPerGroup) {
            throw new RuntimeException(
                String.format("팀 수(%d)가 조 구성(%d개 조 × %d팀)과 맞지 않습니다.",
                    teamCount, groupCount, teamsPerGroup)
            );
        }

        log.info("조별리그 대진표 생성: {}개 조, 조당 {}팀", groupCount, teamsPerGroup);

        // 팀 조회
        Map<Long, Team> teamMap = new HashMap<>();
        for (Long teamId : teamIds) {
            teamRepository.findById(teamId).ifPresent(team -> teamMap.put(teamId, team));
        }

        // 프론트엔드에서 전달받은 순서를 그대로 사용 (사용자가 직접 정렬함)
        List<Long> orderedTeams = new ArrayList<>(teamIds);

        // 조 생성 및 팀 배정
        List<TournamentGroup> groups = new ArrayList<>();
        List<List<Team>> groupTeamsList = new ArrayList<>();

        for (int i = 0; i < groupCount; i++) {
            TournamentGroup group = TournamentGroup.builder()
                    .tournament(tournament)
                    .groupName(generateGroupName(i))
                    .groupOrder(i + 1)
                    .teams(new ArrayList<>())
                    .build();

            List<Team> groupTeams = new ArrayList<>();

            // 조에 팀 배정
            for (int j = 0; j < teamsPerGroup; j++) {
                int teamIndex = i * teamsPerGroup + j;
                Long teamId = orderedTeams.get(teamIndex);
                if (teamMap.containsKey(teamId)) {
                    Team team = teamMap.get(teamId);
                    group.getTeams().add(team);
                    groupTeams.add(team);
                }
            }

            groups.add(groupRepository.save(group));
            groupTeamsList.add(groupTeams);
        }

        // 각 조별 리그 매치 생성 (라운드 로빈)
        int matchNumber = 1;
        for (int g = 0; g < groups.size(); g++) {
            TournamentGroup group = groups.get(g);
            List<Team> groupTeams = groupTeamsList.get(g);

            // 조 내 모든 팀이 한 번씩 경기
            for (int i = 0; i < groupTeams.size(); i++) {
                for (int j = i + 1; j < groupTeams.size(); j++) {
                    TournamentMatch match = TournamentMatch.builder()
                            .tournament(tournament)
                            .round(1) // 조별리그는 모두 1라운드
                            .matchNumber(matchNumber++)
                            .groupId(group.getGroupName())
                            .status(TournamentMatch.MatchStatus.SCHEDULED)
                            .build();

                    // 팀 배정
                    match.assignTeam1(groupTeams.get(i));
                    match.assignTeam2(groupTeams.get(j));

                    matchRepository.save(match);
                }
            }
        }

        log.info("조별리그 총 {}경기 생성", matchNumber - 1);

        // 결선 토너먼트 매치 미리 생성 (팀은 조별리그 종료 후 배정)
        generateEmptyKnockoutMatches(tournament, groupCount, tournament.getAdvanceCount());
    }

    /**
     * 결선 토너먼트 빈 매치 미리 생성 (팀은 나중에 배정)
     */
    private void generateEmptyKnockoutMatches(Tournament tournament, int groupCount, int advanceCount) {
        int knockoutTeamCount = groupCount * advanceCount;
        int bracketSize = nextPowerOfTwo(knockoutTeamCount);
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));

        log.info("결선 토너먼트 빈 매치 생성: {}팀 진출 예정, {}라운드", knockoutTeamCount, totalRounds);

        int knockoutRound = 2; // 조별리그가 round 1
        int currentMatchCount = bracketSize / 2;
        int knockoutMatchNumber = 1;

        // 모든 라운드의 빈 매치 생성
        for (int round = knockoutRound; round < knockoutRound + totalRounds; round++) {
            for (int i = 0; i < currentMatchCount; i++) {
                TournamentMatch match = TournamentMatch.builder()
                        .tournament(tournament)
                        .round(round)
                        .matchNumber(knockoutMatchNumber++)
                        .status(TournamentMatch.MatchStatus.SCHEDULED)
                        .build();
                matchRepository.save(match);
            }
            // 다음 라운드는 경기 수가 절반
            currentMatchCount /= 2;
            knockoutMatchNumber = 1; // 라운드별로 matchNumber 리셋
        }

        // 준결승이 있는 경우 마지막 라운드에 3,4위전 경기 추가 생성
        if (totalRounds >= 2) {
            int finalRound = knockoutRound + totalRounds - 1;
            TournamentMatch thirdPlaceMatch = TournamentMatch.builder()
                    .tournament(tournament)
                    .round(finalRound)
                    .matchNumber(2)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build();
            matchRepository.save(thirdPlaceMatch);
        }

        log.info("결선 토너먼트 빈 매치 생성 완료");
    }

    /**
     * 스위스 시스템 대진표 생성
     */
    private void generateSwissSystemBracket(Tournament tournament, List<Long> teamIds) {
        int teamCount = teamIds.size();
        int rounds = tournament.getSwissRounds();

        if (teamCount % 2 != 0) {
            throw new RuntimeException("스위스 시스템은 짝수 팀이 필요합니다.");
        }

        log.info("스위스 시스템 대진표 생성: {}팀, {}라운드", teamCount, rounds);

        // 팀 조회
        Map<Long, Team> teamMap = new HashMap<>();
        for (Long teamId : teamIds) {
            teamRepository.findById(teamId).ifPresent(team -> teamMap.put(teamId, team));
        }

        // 1라운드만 생성 (이후 라운드는 결과에 따라 동적 생성)
        // 프론트엔드에서 전달받은 순서를 그대로 사용 (사용자가 직접 정렬함)
        List<Long> orderedTeams = new ArrayList<>(teamIds);

        int matchNumber = 1;
        for (int i = 0; i < teamCount; i += 2) {
            TournamentMatch match = TournamentMatch.builder()
                    .tournament(tournament)
                    .round(1)
                    .matchNumber(matchNumber++)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build();

            // 팀 배정
            Long team1Id = orderedTeams.get(i);
            Long team2Id = orderedTeams.get(i + 1);
            if (teamMap.containsKey(team1Id)) {
                match.assignTeam1(teamMap.get(team1Id));
            }
            if (teamMap.containsKey(team2Id)) {
                match.assignTeam2(teamMap.get(team2Id));
            }

            matchRepository.save(match);
        }

        log.info("스위스 1라운드 {}경기 생성", matchNumber - 1);
    }

    /**
     * 다음 2의 거듭제곱 계산
     */
    private int nextPowerOfTwo(int n) {
        int power = 1;
        while (power < n) {
            power *= 2;
        }
        return power;
    }

    private String generateGroupName(int index) {
        int alphabetSize = 26;
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + (value % alphabetSize)));
            value = (value / alphabetSize) - 1;
        } while (value >= 0);
        return name.toString();
    }

    /**
     * 스위스 다음 라운드 생성 (결과 기반)
     */
    @Transactional
    public void generateNextSwissRound(Long tournamentId, int currentRound) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다"));

        if (tournament.getTournamentType() != TournamentType.SWISS_SYSTEM) {
            throw new RuntimeException("스위스 시스템 대회만 가능합니다.");
        }

        // 현재 라운드 결과 확인
        List<TournamentMatch> currentMatches = matchRepository
                .findByTournamentIdAndRound(tournamentId, currentRound);

        boolean allFinished = currentMatches.stream()
                .allMatch(TournamentMatch::isFinished);

        if (!allFinished) {
            throw new RuntimeException("현재 라운드가 모두 종료되지 않았습니다.");
        }

        // TODO: 승점 기반 팀 매칭 로직
        // 같은 승점의 팀끼리 매칭
        
        log.info("스위스 {}라운드 생성 완료", currentRound + 1);
    }
}
