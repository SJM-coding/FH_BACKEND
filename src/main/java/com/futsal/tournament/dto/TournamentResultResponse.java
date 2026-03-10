package com.futsal.tournament.dto;

import com.futsal.team.domain.AwardType;
import com.futsal.tournament.domain.TournamentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 결과 응답
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentResultResponse {

    private Long id;
    private Long tournamentId;
    private Long teamId;
    private String teamName;
    private Integer rank;
    private AwardType awardType;
    private String awardTypeDisplayName;

    public static TournamentResultResponse from(TournamentResult result) {
        return TournamentResultResponse.builder()
                .id(result.getId())
                .tournamentId(result.getTournament().getId())
                .teamId(result.getTeamId())
                .teamName(result.getTeamName())
                .rank(result.getRank())
                .awardType(result.getAwardType())
                .awardTypeDisplayName(result.getAwardType().getDisplayName())
                .build();
    }
}
