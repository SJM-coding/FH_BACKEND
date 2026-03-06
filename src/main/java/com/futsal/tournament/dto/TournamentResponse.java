package com.futsal.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentResponse {

    private Long id;
    private String title;
    private LocalDate tournamentDate;
    private String location;
    private String playerType;
    private String gender;
    private String description;
    private int viewCount;
    private String originalLink;
    private String tournamentType;
    private Integer maxTeams;
    private Integer groupCount;
    private Integer teamsPerGroup;
    private Integer swissRounds;
    private Boolean bracketGenerated;
    private List<String> posterUrls; // 포스터 URL 목록
    private String recruitmentStatus; // OPEN, CLOSED
    private Long registeredById;
    private String registeredByName;
    private LocalDateTime createdAt;
    private Boolean isExternal;
    private String externalUrl;
    private String shareCode;
}
