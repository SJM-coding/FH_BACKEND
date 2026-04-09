package com.futsal.team.presentation.dto;

import com.futsal.team.domain.PlayerPosition;
import com.futsal.team.domain.TeamMemberRole;
import com.futsal.team.domain.TeamMemberStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 팀원 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String profileImageUrl;
    private TeamMemberRole role;
    private TeamMemberStatus status;
    private PlayerPosition position;
    private LocalDateTime joinedAt;
}
