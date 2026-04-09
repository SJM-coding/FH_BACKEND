package com.futsal.tournament.presentation.dto;

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

    // 경기 라벨 (예: 결승, 3·4위전)
    private String matchLabel;

    // 상태
    private String status;
    private LocalDateTime scheduledAt;
    private String venueName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /**
     * Entity -> DTO 변환
     */
    public static MatchResponse from(TournamentMatch match) {
        MatchResponseBuilder builder = MatchResponse.builder()
                .id(match.getId())
                .tournamentId(match.getTournamentId())
                .round(match.getRound())
                .matchNumber(match.getMatchNumber())
                .groupId(match.getGroupId())
                .team1Score(match.getTeam1Score())
                .team2Score(match.getTeam2Score())
                .team1PenaltyScore(match.getTeam1PenaltyScore())
                .team2PenaltyScore(match.getTeam2PenaltyScore())
                .status(match.getStatus().name())
                .scheduledAt(match.getScheduledAt())
                .venueName(match.getVenueName())
                .startedAt(match.getStartedAt())
                .finishedAt(match.getFinishedAt());

        builder.team1Id(match.getTeam1Id())
               .team1Name(match.getTeam1Name())
               .team1LogoUrl(match.getTeam1LogoUrl())
               .team2Id(match.getTeam2Id())
               .team2Name(match.getTeam2Name())
               .team2LogoUrl(match.getTeam2LogoUrl())
               .winnerId(match.getWinnerId())
               .winnerName(match.getWinnerName());

        return builder.build();
    }
}
