package com.futsal.tournament.application;

import com.futsal.team.domain.AwardType;
import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamAward;
import com.futsal.team.repository.TeamAwardRepository;
import com.futsal.team.repository.TeamRepository;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentResult;
import com.futsal.tournament.presentation.dto.TournamentResultRequest;
import com.futsal.tournament.presentation.dto.TournamentResultResponse;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.infrastructure.TournamentResultRepository;
import com.futsal.user.domain.User;
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
            // 기존 자동 생성된 수상 경력도 삭제
            teamAwardRepository.deleteByTournamentId(tournamentId);
            resultRepository.deleteByTournamentId(tournamentId);
        }

        List<TournamentResult> results = new ArrayList<>();

        for (TournamentResultRequest.TeamRank teamRank : request.getResults()) {
            Team team = teamRepository.findById(teamRank.getTeamId())
                    .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamRank.getTeamId()));

            AwardType awardType = TournamentResult.getAwardTypeByRank(teamRank.getRank());
            if (awardType == null) {
                continue; // 5위 이하는 수상 기록 안 함
            }

            // 대회 결과 저장
            TournamentResult result = TournamentResult.builder()
                    .tournament(tournament)
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .rank(teamRank.getRank())
                    .awardType(awardType)
                    .build();
            results.add(resultRepository.save(result));

            // 팀 수상 경력 자동 생성
            TeamAward award = TeamAward.builder()
                    .team(team)
                    .tournamentId(tournamentId)
                    .tournamentName(tournament.getTitle())
                    .awardType(awardType)
                    .awardDate(tournament.getTournamentDate())
                    .description(null)
                    .build();
            teamAwardRepository.save(award);
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

        // 자동 생성된 수상 경력도 삭제
        teamAwardRepository.deleteByTournamentId(tournamentId);
        resultRepository.deleteByTournamentId(tournamentId);
    }
}
