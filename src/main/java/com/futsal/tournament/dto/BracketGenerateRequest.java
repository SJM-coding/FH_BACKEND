package com.futsal.tournament.dto;

import com.futsal.tournament.domain.TournamentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대진표 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BracketGenerateRequest {

    /**
     * 참가 팀 ID 목록
     */
    private List<Long> participatingTeamIds;

    /**
     * 조별리그 설정 (GROUP_STAGE인 경우만)
     */
    private Integer groupCount;
    private Integer teamsPerGroup;

    /**
     * 스위스 시스템 설정 (SWISS_SYSTEM인 경우만)
     */
    private Integer swissRounds;
}
