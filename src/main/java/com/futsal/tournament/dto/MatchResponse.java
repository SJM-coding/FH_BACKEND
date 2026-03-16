package com.futsal.tournament.dto;

import com.futsal.tournament.domain.TournamentMatch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 경기 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {

    private Long id;
    private Long tournamentId;
    private Integer round;
    private Integer matchNumber;
    private String groupId;

    // Team 1
    private Long team1Id;
    private String team1Name;
    private String team1LogoUrl;
    private Integer team1Score;
    private Integer team1PenaltyScore;

    // Team 2
    private Long team2Id;
    private String team2Name;
    private String team2LogoUrl;
    private Integer team2Score;
    private Integer team2PenaltyScore;

    // 승자
    private Long winnerId;
    private String winnerName;

    // 상태
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /**
     * Entity -> DTO 변환
     */
    public static MatchResponse from(TournamentMatch match) {
        MatchResponseBuilder builder = MatchResponse.builder()
                .id(match.getId())
                .tournamentId(match.getTournament().getId())
                .round(match.getRound())
                .matchNumber(match.getMatchNumber())
                .groupId(match.getGroupId())
                .team1Score(match.getTeam1Score())
                .team2Score(match.getTeam2Score())
                .team1PenaltyScore(match.getTeam1PenaltyScore())
                .team2PenaltyScore(match.getTeam2PenaltyScore())
                .status(match.getStatus().name())
                .scheduledAt(match.getScheduledAt())
                .startedAt(match.getStartedAt())
                .finishedAt(match.getFinishedAt());

        // Team 1
        if (match.getTeam1() != null) {
            builder.team1Id(match.getTeam1().getId())
                   .team1Name(match.getTeam1().getName())
                   .team1LogoUrl(match.getTeam1().getLogoUrl());
        }

        // Team 2
        if (match.getTeam2() != null) {
            builder.team2Id(match.getTeam2().getId())
                   .team2Name(match.getTeam2().getName())
                   .team2LogoUrl(match.getTeam2().getLogoUrl());
        }

        // Winner
        if (match.getWinner() != null) {
            builder.winnerId(match.getWinner().getId())
                   .winnerName(match.getWinner().getName());
        }

        return builder.build();
    }
}
