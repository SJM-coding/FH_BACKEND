package com.futsal.team.presentation.dto;

import lombok.Data;

/**
 * 팀 수정 요청 DTO
 */
@Data
public class TeamUpdateRequest {
    private String name;
    private String region;
    private String logoUrl;
}
