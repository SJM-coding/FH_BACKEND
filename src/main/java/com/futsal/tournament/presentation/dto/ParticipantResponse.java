package com.futsal.tournament.presentation.dto;

import com.futsal.tournament.domain.TournamentParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 참가팀 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantResponse {
    private Long id;
    private Long tournamentId;
    private Long teamId;
    private String teamName;
    private String teamLogoUrl;
    private Long registeredBy;
    private String status;
    private LocalDateTime createdAt;

    public static ParticipantResponse from(TournamentParticipant participant) {
        return ParticipantResponse.builder()
                .id(participant.getId())
                .tournamentId(participant.getTournament().getId())
                .teamId(participant.getTeamId())
                .teamName(participant.getTeamName())
                .teamLogoUrl(participant.getTeamLogoUrl())
                .registeredBy(participant.getRegisteredBy())
                .status(participant.getStatus().name())
                .createdAt(participant.getCreatedAt())
                .build();
    }
}
