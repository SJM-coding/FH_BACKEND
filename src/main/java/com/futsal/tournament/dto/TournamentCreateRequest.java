package com.futsal.tournament.dto;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.TournamentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentCreateRequest {

    @NotBlank(message = "대회 이름은 필수입니다")
    private String title;

    @NotNull(message = "대회 날짜는 필수입니다")
    private LocalDate tournamentDate;

    @NotBlank(message = "장소는 필수입니다")
    private String location;

    @NotNull(message = "선출 타입은 필수입니다")
    private PlayerType playerType;

    @NotNull(message = "성별은 필수입니다")
    private Gender gender;

    @NotBlank(message = "설명은 필수입니다")
    private String description;

    private String originalLink; // 선택사항

    private TournamentType tournamentType;
    private Integer maxTeams;
    private Integer groupCount;
    private Integer teamsPerGroup;
    private Integer swissRounds;

    private List<String> posterUrls; // S3 업로드 후 URL 목록
    
    private Boolean isExternal; // 외부 대회 여부
    private String externalUrl; // 외부 대회 URL
}
