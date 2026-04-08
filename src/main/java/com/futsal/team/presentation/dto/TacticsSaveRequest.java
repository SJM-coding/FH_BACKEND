package com.futsal.team.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 전술 저장 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacticsSaveRequest {
    private String formation;
    private List<TacticsPlayerPosition> players;
    private String framesJson; // 애니메이션 프레임 JSON (최대 10개)
}
