package com.futsal.team.presentation.dto;

import lombok.Data;

/**
 * 팀 생성 요청 DTO
 */
@Data
public class TeamCreateRequest {
    private String name;
    private String region;
    private String logoUrl;
}
