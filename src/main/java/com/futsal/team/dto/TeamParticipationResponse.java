package com.futsal.team.dto;

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

    public static TeamParticipationResponse from(TournamentParticipant participant) {
        Tournament t = participant.getTournament();
        return TeamParticipationResponse.builder()
                .participationId(participant.getId())
                .tournamentId(t.getId())
                .tournamentTitle(t.getTitle())
                .tournamentDate(t.getTournamentDate())
                .location(t.getLocation())
                .posterUrl(t.getPosterUrls() != null && !t.getPosterUrls().isEmpty()
                        ? t.getPosterUrls().get(0) : null)
                .status(participant.getStatus().name())
                .statusDisplayName(participant.getStatus().getDescription())
                .build();
    }
}
