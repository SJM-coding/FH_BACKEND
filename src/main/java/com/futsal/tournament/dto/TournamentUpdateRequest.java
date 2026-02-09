package com.futsal.tournament.dto;

import lombok.Data;

import java.util.List;

/**
 * 대회 수정 요청 DTO
 */
@Data
public class TournamentUpdateRequest {
    private String title;
    private java.time.LocalDate tournamentDate;
    private String location;
    private String gender;
    private String playerType;
    private String description;
    private String originalLink;
    private List<String> posterUrls; // 포스터 URL 목록
    private String recruitmentStatus; // OPEN, CLOSED
}
