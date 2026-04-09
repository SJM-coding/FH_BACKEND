package com.futsal.team.presentation.dto;

import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 팀의 대회 참가 이력 응답
 */
@Data
@Builder
public class TeamParticipationResponse {

    private Long participationId;
    private Long tournamentId;
    private String tournamentTitle;
    private LocalDate tournamentDate;
    private String location;
    private String posterUrl;
    private String status;
    private String statusDisplayName;

    public static TeamParticipationResponse from(
            TournamentParticipant participant, Tournament tournament) {
        return TeamParticipationResponse.builder()
                .participationId(participant.getId())
                .tournamentId(tournament.getId())
                .tournamentTitle(tournament.getTitle())
                .tournamentDate(tournament.getTournamentDate())
                .location(tournament.getLocation())
                .posterUrl(tournament.getPosterUrls() != null
                        && !tournament.getPosterUrls().isEmpty()
                        ? tournament.getPosterUrls().get(0) : null)
                .status(participant.getStatus().name())
                .statusDisplayName(participant.getStatus().getDescription())
                .build();
    }
}
