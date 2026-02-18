package com.futsal.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대진표 전체 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BracketResponse {

    private Long tournamentId;
    private String tournamentTitle;
    private String tournamentType;
    private Integer totalRounds;
    private Integer currentRound;
    private Boolean bracketGenerated;

    /**
     * 라운드별 경기 목록
     */
    private List<RoundMatches> rounds;

    /**
     * 조별리그인 경우 그룹 정보
     */
    private List<GroupInfo> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundMatches {
        private Integer round;
        private String roundName; // "16강", "8강", "준결승", "결승"
        private List<MatchResponse> matches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupInfo {
        private String groupName;
        private List<TeamStanding> standings;
        private List<MatchResponse> matches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamStanding {
        private Long teamId;
        private String teamName;
        private String teamLogoUrl;
        private Integer played;
        private Integer won;
        private Integer drawn;
        private Integer lost;
        private Integer goalsFor;
        private Integer goalsAgainst;
        private Integer goalDifference;
        private Integer points;
        private Integer rank;
    }
}
