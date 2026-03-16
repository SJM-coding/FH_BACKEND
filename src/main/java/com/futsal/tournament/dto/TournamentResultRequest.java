package com.futsal.tournament.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대회 결과 입력 요청
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentResultRequest {

    @NotEmpty(message = "결과를 입력해주세요")
    @Valid
    private List<TeamRank> results;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamRank {
        @NotNull(message = "팀 ID를 입력해주세요")
        private Long teamId;

        @NotNull(message = "순위를 입력해주세요")
        @Min(value = 1, message = "순위는 1 이상이어야 합니다")
        @Max(value = 4, message = "순위는 4 이하여야 합니다")
        private Integer rank;
    }
}
