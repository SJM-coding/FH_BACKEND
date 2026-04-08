package com.futsal.tournament.presentation.dto;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.TournamentType;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 대회 수정 요청 DTO
 */
@Data
public class TournamentUpdateRequest {
    private String title;
    private LocalDate tournamentDate;
    private String location;
    private Gender gender;
    private PlayerType playerType;
    private String description;
    private String originalLink;
    private TournamentType tournamentType;
    private Integer maxTeams;
    private Integer groupCount;
    private Integer teamsPerGroup;
    private Integer advanceCount;  // 조별리그: 각 조당 결선 진출 팀 수
    private Integer swissRounds;
    private List<String> posterUrls; // 포스터 URL 목록
    private String recruitmentStatus; // OPEN, CLOSED
    private Boolean isExternal; // 외부 대회 여부
    private String externalUrl; // 외부 대회 URL
}
