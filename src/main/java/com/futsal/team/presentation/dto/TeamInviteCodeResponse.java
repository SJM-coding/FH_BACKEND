package com.futsal.team.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 팀 초대 코드 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamInviteCodeResponse {
    private String inviteCode;
    private Long teamId;
    private String teamName;
}
