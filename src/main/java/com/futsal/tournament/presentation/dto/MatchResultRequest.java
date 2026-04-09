package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 경기 결과 입력 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultRequest {

    private Integer team1Score;
    private Integer team2Score;
    private Integer team1PenaltyScore;
    private Integer team2PenaltyScore;
}
