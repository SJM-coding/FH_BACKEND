package com.futsal.tournament.dto;

import lombok.Data;

import java.util.List;
import com.futsal.tournament.domain.TournamentType;

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
    private TournamentType tournamentType;
    private Integer maxTeams;
    private Integer groupCount;
    private Integer teamsPerGroup;
    private Integer swissRounds;
    private List<String> posterUrls; // 포스터 URL 목록
    private String recruitmentStatus; // OPEN, CLOSED
    private Boolean isExternal; // 외부 대회 여부
    private String externalUrl; // 외부 대회 URL
}
