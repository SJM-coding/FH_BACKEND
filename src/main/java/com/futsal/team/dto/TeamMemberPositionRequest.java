package com.futsal.team.dto;

import com.futsal.team.domain.PlayerPosition;
import lombok.Data;

/**
 * 팀원 포지션 변경 요청 DTO
 */
@Data
public class TeamMemberPositionRequest {
    private PlayerPosition position;
}
