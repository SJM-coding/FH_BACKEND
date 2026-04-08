package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 결선 1라운드 팀 재배치 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnockoutMatchAssignmentRequest {

    private List<MatchAssignment> assignments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchAssignment {
        private Long matchId;
        private Long team1Id;
        private Long team2Id;
    }
}
