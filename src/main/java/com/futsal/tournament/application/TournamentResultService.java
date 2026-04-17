package com.futsal.tournament.application;

import com.futsal.team.domain.AwardType;
import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamAward;
import com.futsal.team.infrastructure.TeamAwardRepository;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipantMember;
import com.futsal.tournament.domain.TournamentResult;
import com.futsal.tournament.infrastructure.TournamentParticipantMemberRepository;
import com.futsal.tournament.presentation.dto.TournamentResultRequest;
import com.futsal.tournament.presentation.dto.TournamentResultResponse;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.infrastructure.TournamentResultRepository;
import com.futsal.user.domain.User;
import com.futsal.user.domain.UserAward;
import com.futsal.user.infrastructure.UserAwardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 대회 결과 관리 서비스
 * 결과 입력 시 자동으로 팀 수상 경력 생성
 */
@Service
@RequiredArgsConstructor
public class TournamentResultService {

    private final TournamentRepository tournamentRepository;
    private final TournamentResultRepository resultRepository;
    private final TeamRepository teamRepository;
    private final TeamAwardRepository teamAwardRepository;
    private final TournamentParticipantMemberRepository participantMemberRepository;
    private final UserAwardRepository userAwardRepository;

    /**
     * 대회 결과 입력 (개최자만 가능)
     * 자동으로 팀 수상 경력 생성
     */
    @Transactional
    public List<TournamentResultResponse> recordResults(Long tournamentId, TournamentResultRequest request, User user) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인 (개최자만)
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회 결과를 입력할 권한이 없습니다");
        }

        // 기존 결과가 있으면 삭제 (덮어쓰기)
        if (resultRepository.existsByTournamentId(tournamentId)) {
            userAwardRepository.deleteByTournamentId(tournamentId);
            teamAwardRepository.deleteByTournamentId(tournamentId);
            resultRepository.deleteByTournamentId(tournamentId);
        }

        List<TournamentResult> results = new ArrayList<>();

        for (TournamentResultRequest.TeamRank teamRank : request.getResults()) {
            Team team = teamRepository.findById(teamRank.getTeamId())
                    .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamRank.getTeamId()));

            AwardType awardType = TournamentResult.getAwardTypeByRank(teamRank.getRank());
            if (awardType == null) continue;

            // TournamentResult Aggregate 저장
            TournamentResult result = TournamentResult.builder()
                    .tournamentId(tournament.getId())
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .rank(teamRank.getRank())
                    .awardType(awardType)
                    .build();
            results.add(resultRepository.save(result));

            // Cross-BC: 팀 수상 경력 생성 (Team BC)
            teamAwardRepository.save(TeamAward.builder()
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .tournamentId(tournamentId)
                    .tournamentName(tournament.getTitle())
                    .awardType(awardType)
                    .awardDate(tournament.getTournamentDate())
                    .description(null)
                    .build());

            // Cross-BC: 참가 시점 스냅샷 기반 개인 수상 뱃지 생성 (Identity BC)
            List<TournamentParticipantMember> members =
                participantMemberRepository.findByTournamentIdAndTeamId(tournamentId, team.getId());
            List<UserAward> userAwards = members.stream()
                .map(m -> UserAward.builder()
                    .userId(m.getUserId())
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .organizerUserId(tournament.getRegisteredBy().getId())
                    .organizerName(tournament.getRegisteredBy().getNickname())
                    .tournamentId(tournamentId)
                    .tournamentName(tournament.getTitle())
                    .awardType(awardType)
                    .awardDate(tournament.getTournamentDate())
                    .build())
                .toList();
            userAwardRepository.saveAll(userAwards);
        }

        return results.stream()
                .map(TournamentResultResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 대회 결과 조회
     */
    @Transactional(readOnly = true)
    public List<TournamentResultResponse> getResults(Long tournamentId) {
        // 대회 존재 확인
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        List<TournamentResult> results = resultRepository.findByTournamentId(tournamentId);
        return results.stream()
                .map(TournamentResultResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 대회 결과 삭제 (개최자만 가능)
     */
    @Transactional
    public void deleteResults(Long tournamentId, User user) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회 결과를 삭제할 권한이 없습니다");
        }

        userAwardRepository.deleteByTournamentId(tournamentId);
        teamAwardRepository.deleteByTournamentId(tournamentId);
        resultRepository.deleteByTournamentId(tournamentId);
    }
}
