package com.futsal.team.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 전술 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacticsResponse {
    private Long id;
    private Long teamId;
    private String formation;
    private List<TacticsPlayerPosition> players;
    private String framesJson; // 애니메이션 프레임 JSON
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
