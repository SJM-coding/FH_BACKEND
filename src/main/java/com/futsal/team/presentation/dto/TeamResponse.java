package com.futsal.team.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 팀 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {
    private Long id;
    private String name;
    private String region;
    private String logoUrl;
    private Long captainId;
    private String captainName;
    private Integer memberCount;
    private LocalDateTime createdAt;
}
