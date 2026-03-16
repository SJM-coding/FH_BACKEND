package com.futsal.team.dto;

import lombok.Data;

/**
 * 팀 초대 요청 DTO
 */
@Data
public class TeamInviteRequest {
    private Long userId;  // 초대할 사용자 ID
}
