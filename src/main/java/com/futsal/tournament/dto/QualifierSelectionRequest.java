package com.futsal.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 조별리그 진출팀 수동 선택 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualifierSelectionRequest {

    /**
     * 각 조별 진출팀 ID 목록
     * Key: 조 이름 (예: "A", "B")
     * Value: 진출할 팀 ID 목록 (순서대로 1위, 2위)
     */
    private Map<String, List<Long>> qualifiedTeamsByGroup;
}
