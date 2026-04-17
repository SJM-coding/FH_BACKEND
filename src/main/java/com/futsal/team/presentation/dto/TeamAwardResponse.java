package com.futsal.team.presentation.dto;

import com.futsal.team.domain.TeamAward;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class TeamAwardResponse {

    private Long id;
    private Long teamId;
    private Long tournamentId;
    private String tournamentName;
    private String awardType;
    private String awardTypeDisplayName;
    private LocalDate awardDate;
    private String description;

    public static TeamAwardResponse from(TeamAward award) {
        return TeamAwardResponse.builder()
                .id(award.getId())
                .teamId(award.getTeamId())
                .tournamentId(award.getTournamentId())
                .tournamentName(award.getTournamentName())
                .awardType(award.getAwardType().name())
                .awardTypeDisplayName(award.getAwardType().getDisplayName())
                .awardDate(award.getAwardDate())
                .description(award.getDescription())
                .build();
    }
}
