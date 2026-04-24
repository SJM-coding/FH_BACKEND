package com.futsal.tournament.application;

import com.futsal.team.domain.Team;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.presentation.dto.BracketGenerateRequest;
import com.futsal.tournament.domain.*;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.tournament.infrastructure.TournamentGroupRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final BracketRepository bracketRepository;

    /**
     * 대진표 생성 (참가 팀 기반)
     */
    @Transactional
    public void generateBracket(
        Long tournamentId,
        BracketGenerateRequest request,
        User user
    ) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));
        verifyOwner(tournament, user);
        List<Long> participatingTeamIds = request.getParticipatingTeamIds();

        if (participatingTeamIds == null || participatingTeamIds.isEmpty()) {
            throw new RuntimeException("참가 팀 정보가 없습니다.");
        }

        // 외부 대회는 자동 생성 불가
        if (tournament.getTournamentType() == TournamentType.EXTERNAL) {
            throw new RuntimeException("외부 대회는 이미지 대진표만 등록할 수 있습니다.");
        }

        Bracket bracket = bracketRepository.findByTournamentId(tournamentId)
            .orElseGet(() -> Bracket.createDefault(tournamentId));

        if (bracket.isGenerated()) {
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

        // 대진표 생성 타입별 분기 (메모리에서 구성 후 Tournament을 통해 저장)
        switch (tournament.getTournamentType()) {
            case SINGLE_ELIMINATION:
                buildSingleEliminationBracket(tournament, participatingTeamIds);
                break;
            case GROUP_STAGE:
                buildGroupStageBracket(tournament, participatingTeamIds);
                break;
            case SPLIT_STAGE:
                // 풋투풋: 조별리그만 생성 (결선은 SplitBracketService가 담당)
                buildSplitStageBracket(tournament, participatingTeamIds);
                break;
            case SWISS_SYSTEM:
                buildSwissSystemBracket(tournament, participatingTeamIds);
                break;
            case EXTERNAL:
                throw new RuntimeException(
                    "외부 대회는 자동 대진표 생성이 불가능합니다.");
        }

        bracket.markGenerated();
        bracketRepository.save(bracket);

        log.info("대진표 생성 완료: 대회 ID={}, 타입={}, 팀 수={}",
            tournamentId, tournament.getTournamentType(), participatingTeamIds.size());
    }

    private void verifyOwner(Tournament tournament, User user) {
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대진표를 생성할 권한이 없습니다.");
        }
    }

    private void applyGenerationSettings(
        Tournament tournament, BracketGenerateRequest request, int teamCount
    ) {
        switch (tournament.getTournamentType()) {
            case GROUP_STAGE -> {
                applyGroupSettings(tournament, request, teamCount);
                if (tournament.getAdvanceCount() == null
                    || tournament.getAdvanceCount() < 1) {
                    tournament.setAdvanceCount(2);
                }
            }
            case SPLIT_STAGE -> {
                // 풋투풋도 조별 설정 유효성 검사 (advanceCount는 사용 안 함)
                applyGroupSettings(tournament, request, teamCount);
            }
            case SWISS_SYSTEM -> {
                if (request.getSwissRounds() != null) {
                    tournament.setSwissRounds(request.getSwissRounds());
                }
                if (tournament.getSwissRounds() == null
                    || tournament.getSwissRounds() < 1) {
                    throw new RuntimeException(
                        "스위스 시스템 라운드 수가 올바르지 않습니다.");
                }
            }
            default -> {
            }
        }
    }

    private void applyGroupSettings(
        Tournament tournament, BracketGenerateRequest request, int teamCount
    ) {
        if (request.getGroupCount() != null) {
            tournament.setGroupCount(request.getGroupCount());
        }
        if (request.getTeamsPerGroup() != null) {
            tournament.setTeamsPerGroup(request.getTeamsPerGroup());
        }
        if (tournament.getGroupCount() == null
            || tournament.getTeamsPerGroup() == null) {
            throw new RuntimeException("조별리그 설정이 없습니다.");
        }
        if (!Objects.equals(
            tournament.getGroupCount() * tournament.getTeamsPerGroup(), teamCount)
        ) {
            throw new RuntimeException(
                String.format(
                    "참가 팀 수(%d)가 조 구성(%d개 조 × %d팀)과 맞지 않습니다.",
                    teamCount,
                    tournament.getGroupCount(),
                    tournament.getTeamsPerGroup()));
        }
    }

    /**
     * 싱글 엘리미네이션 대진표 생성
     * 경기 객체를 메모리에서 구성 후 Tournament을 통해 일괄 저장
     */
    private void buildSingleEliminationBracket(Tournament tournament, List<Long> teamIds) {
        int teamCount = teamIds.size();
        int bracketSize = nextPowerOfTwo(teamCount);
        int byeCount = bracketSize - teamCount;

        log.info("토너먼트 대진표 생성: 총 {}팀, 브라켓 크기={}, 부전승={}", teamCount, bracketSize, byeCount);

        Map<Long, Team> teamMap = loadTeams(teamIds);
        List<Long> orderedTeams = new ArrayList<>(teamIds);
        for (int i = 0; i < byeCount; i++) orderedTeams.add(null);

        List<TournamentMatch> newMatches = new ArrayList<>();

        // 1라운드 매치 생성
        int matchNumber = 1;
        for (int i = 0; i < bracketSize; i += 2) {
            TournamentMatch match = TournamentMatch.builder()
                    .tournamentId(tournament.getId())
                    .round(1)
                    .matchNumber(matchNumber++)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build();

            Long team1Id = orderedTeams.get(i);
            Long team2Id = orderedTeams.get(i + 1);
            if (team1Id != null && teamMap.containsKey(team1Id)) {
                Team t = teamMap.get(team1Id);
                match.assignTeam1(t.getId(), t.getName(), t.getLogoUrl());
            }
            if (team2Id != null && teamMap.containsKey(team2Id)) {
                Team t = teamMap.get(team2Id);
                match.assignTeam2(t.getId(), t.getName(), t.getLogoUrl());
            }

            newMatches.add(match);
        }

        // 이후 라운드 빈 매치 생성
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));
        int currentMatchCount = bracketSize / 2;
        for (int round = 2; round <= totalRounds; round++) {
            currentMatchCount /= 2;
            for (int i = 1; i <= currentMatchCount; i++) {
                newMatches.add(TournamentMatch.builder()
                        .tournamentId(tournament.getId())
                        .round(round)
                        .matchNumber(i)
                        .status(TournamentMatch.MatchStatus.SCHEDULED)
                        .build());
            }
        }

        matchRepository.saveAll(newMatches);
    }

    /**
     * 조별 리그 대진표 생성
     * 조/경기 객체를 메모리에서 구성 후 Tournament을 통해 일괄 저장
     */
    private void buildGroupStageBracket(Tournament tournament, List<Long> teamIds) {
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

        Map<Long, Team> teamMap = loadTeams(teamIds);
        List<Long> orderedTeams = new ArrayList<>(teamIds);

        List<TournamentGroup> newGroups = new ArrayList<>();
        List<TournamentMatch> newMatches = new ArrayList<>();
        int matchNumber = 1;

        for (int i = 0; i < groupCount; i++) {
            TournamentGroup group = TournamentGroup.builder()
                    .tournamentId(tournament.getId())
                    .groupName(generateGroupName(i))
                    .groupOrder(i + 1)
                    .build();

            List<Team> groupTeams = new ArrayList<>();
            for (int j = 0; j < teamsPerGroup; j++) {
                Long teamId = orderedTeams.get(i * teamsPerGroup + j);
                if (teamMap.containsKey(teamId)) {
                    Team team = teamMap.get(teamId);
                    group.addTeamId(team.getId());
                    groupTeams.add(team);
                }
            }
            newGroups.add(group);

            // 조 내 라운드 로빈 경기 생성
            for (int a = 0; a < groupTeams.size(); a++) {
                for (int b = a + 1; b < groupTeams.size(); b++) {
                    TournamentMatch match = TournamentMatch.builder()
                            .tournamentId(tournament.getId())
                            .round(1)
                            .matchNumber(matchNumber++)
                            .groupId(group.getGroupName())
                            .status(TournamentMatch.MatchStatus.SCHEDULED)
                            .build();
                    Team ta = groupTeams.get(a);
                    Team tb = groupTeams.get(b);
                    match.assignTeam1(ta.getId(), ta.getName(), ta.getLogoUrl());
                    match.assignTeam2(tb.getId(), tb.getName(), tb.getLogoUrl());
                    newMatches.add(match);
                }
            }
        }

        log.info("조별리그 총 {}경기 생성", matchNumber - 1);

        // 결선 토너먼트 빈 경기 생성
        newMatches.addAll(buildEmptyKnockoutMatches(
            tournament, groupCount, tournament.getAdvanceCount()));

        groupRepository.saveAll(newGroups);
        matchRepository.saveAll(newMatches);
    }

    /**
     * 풋투풋(SPLIT_STAGE) 대진표 생성 — 조별리그만 생성
     * 결선(UPPER/LOWER 분리 토너먼트)은 SplitBracketService가 담당.
     */
    private void buildSplitStageBracket(Tournament tournament, List<Long> teamIds) {
        int teamCount = teamIds.size();
        int groupCount = tournament.getGroupCount();
        int teamsPerGroup = tournament.getTeamsPerGroup();

        if (teamCount != groupCount * teamsPerGroup) {
            throw new RuntimeException(
                String.format(
                    "팀 수(%d)가 조 구성(%d개 조 × %d팀)과 맞지 않습니다.",
                    teamCount, groupCount, teamsPerGroup)
            );
        }

        log.info("풋투풋 조별리그 생성: {}개 조, 조당 {}팀",
            groupCount, teamsPerGroup);

        Map<Long, Team> teamMap = loadTeams(teamIds);
        List<Long> orderedTeams = new ArrayList<>(teamIds);

        List<TournamentGroup> newGroups = new ArrayList<>();
        List<TournamentMatch> newMatches = new ArrayList<>();
        int matchNumber = 1;

        for (int i = 0; i < groupCount; i++) {
            TournamentGroup group = TournamentGroup.builder()
                    .tournamentId(tournament.getId())
                    .groupName(generateGroupName(i))
                    .groupOrder(i + 1)
                    .build();

            List<Team> groupTeams = new ArrayList<>();
            for (int j = 0; j < teamsPerGroup; j++) {
                Long teamId = orderedTeams.get(i * teamsPerGroup + j);
                if (teamMap.containsKey(teamId)) {
                    Team team = teamMap.get(teamId);
                    group.addTeamId(team.getId());
                    groupTeams.add(team);
                }
            }
            newGroups.add(group);

            for (int a = 0; a < groupTeams.size(); a++) {
                for (int b = a + 1; b < groupTeams.size(); b++) {
                    TournamentMatch match = TournamentMatch.builder()
                            .tournamentId(tournament.getId())
                            .round(1)
                            .matchNumber(matchNumber++)
                            .groupId(group.getGroupName())
                            .status(TournamentMatch.MatchStatus.SCHEDULED)
                            .build();
                    Team ta = groupTeams.get(a);
                    Team tb = groupTeams.get(b);
                    match.assignTeam1(ta.getId(), ta.getName(), ta.getLogoUrl());
                    match.assignTeam2(tb.getId(), tb.getName(), tb.getLogoUrl());
                    newMatches.add(match);
                }
            }
        }

        log.info("풋투풋 조별리그 총 {}경기 생성 (결선은 운영진이 생성)",
            matchNumber - 1);

        groupRepository.saveAll(newGroups);
        matchRepository.saveAll(newMatches);
    }

    /**
     * 결선 토너먼트 빈 매치 목록 반환 (팀은 나중에 배정)
     */
    private List<TournamentMatch> buildEmptyKnockoutMatches(
        Tournament tournament, int groupCount, int advanceCount
    ) {
        int knockoutTeamCount = groupCount * advanceCount;
        int bracketSize = nextPowerOfTwo(knockoutTeamCount);
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));

        log.info("결선 토너먼트 빈 매치 생성: {}팀 진출 예정, {}라운드", knockoutTeamCount, totalRounds);

        List<TournamentMatch> knockoutMatches = new ArrayList<>();
        int knockoutRound = 2;
        int currentMatchCount = bracketSize / 2;

        for (int round = knockoutRound; round < knockoutRound + totalRounds; round++) {
            for (int i = 1; i <= currentMatchCount; i++) {
                knockoutMatches.add(TournamentMatch.builder()
                        .tournamentId(tournament.getId())
                        .round(round)
                        .matchNumber(i)
                        .status(TournamentMatch.MatchStatus.SCHEDULED)
                        .build());
            }
            currentMatchCount /= 2;
        }

        // 3,4위전 추가
        if (totalRounds >= 2) {
            int finalRound = knockoutRound + totalRounds - 1;
            knockoutMatches.add(TournamentMatch.builder()
                    .tournamentId(tournament.getId())
                    .round(finalRound)
                    .matchNumber(2)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build());
        }

        return knockoutMatches;
    }

    /**
     * 스위스 시스템 대진표 생성
     */
    private void buildSwissSystemBracket(Tournament tournament, List<Long> teamIds) {
        int teamCount = teamIds.size();

        if (teamCount % 2 != 0) {
            throw new RuntimeException("스위스 시스템은 짝수 팀이 필요합니다.");
        }

        log.info("스위스 시스템 대진표 생성: {}팀, {}라운드", teamCount, tournament.getSwissRounds());

        Map<Long, Team> teamMap = loadTeams(teamIds);
        List<Long> orderedTeams = new ArrayList<>(teamIds);
        List<TournamentMatch> newMatches = new ArrayList<>();

        int matchNumber = 1;
        for (int i = 0; i < teamCount; i += 2) {
            TournamentMatch match = TournamentMatch.builder()
                    .tournamentId(tournament.getId())
                    .round(1)
                    .matchNumber(matchNumber++)
                    .status(TournamentMatch.MatchStatus.SCHEDULED)
                    .build();

            Long team1Id = orderedTeams.get(i);
            Long team2Id = orderedTeams.get(i + 1);
            if (teamMap.containsKey(team1Id)) {
                Team t = teamMap.get(team1Id);
                match.assignTeam1(t.getId(), t.getName(), t.getLogoUrl());
            }
            if (teamMap.containsKey(team2Id)) {
                Team t = teamMap.get(team2Id);
                match.assignTeam2(t.getId(), t.getName(), t.getLogoUrl());
            }

            newMatches.add(match);
        }

        log.info("스위스 1라운드 {}경기 생성", matchNumber - 1);
        matchRepository.saveAll(newMatches);
    }

    /**
     * 팀 ID 목록으로 팀 Map 조회
     */
    private Map<Long, Team> loadTeams(List<Long> teamIds) {
        Map<Long, Team> teamMap = new HashMap<>();
        for (Long teamId : teamIds) {
            teamRepository.findById(teamId).ifPresent(team -> teamMap.put(teamId, team));
        }
        return teamMap;
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
