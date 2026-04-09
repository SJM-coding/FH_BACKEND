package com.futsal.tournament.presentation.dto;

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
     * 대진표 생성 방식 (AUTO: 자동 생성, MANUAL: 이미지 업로드)
     */
    private String bracketType;

    /**
     * 대진표 이미지 URL 목록 (MANUAL 타입일 때만 사용)
     */
    private List<String> bracketImageUrls;

    /**
     * 조별리그 완료 여부 (모든 조의 경기 완료)
     */
    private Boolean groupStageCompleted;

    /**
     * 진출팀 선택 필요 여부 (동점으로 인해)
     */
    private Boolean needsQualifierSelection;

    /**
     * 결선 토너먼트 생성 완료 여부
     */
    private Boolean knockoutGenerated;

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

        /**
         * 조별리그 완료 여부
         */
        private Boolean groupCompleted;

        /**
         * 진출권 동점 여부 (2위와 3위가 동점)
         */
        private Boolean hasTie;

        /**
         * 동점인 팀 ID 목록
         */
        private List<Long> tiedTeamIds;
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
