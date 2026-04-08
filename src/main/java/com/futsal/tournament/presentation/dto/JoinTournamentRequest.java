package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 대회 참가 신청 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinTournamentRequest {
    private String shareCode;
    private Long teamId;
}
