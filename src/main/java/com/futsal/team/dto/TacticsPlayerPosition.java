package com.futsal.team.dto;

import com.futsal.team.domain.PlayerPosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전술 보드 선수 위치 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacticsPlayerPosition {
    private Long memberId;
    private String memberName;
    private PlayerPosition position;
    private Integer number;
    private Double x;
    private Double y;
}
