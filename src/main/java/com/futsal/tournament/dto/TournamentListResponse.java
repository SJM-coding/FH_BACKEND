package com.futsal.tournament.dto;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentListResponse {

    private Long id;
    private String title;
    private LocalDate tournamentDate;
    private String location;
    private String recruitmentStatus;
    private String posterUrl; // 첫 번째 포스터만
    private String registeredByName;
    private Gender gender;
    private PlayerType playerType;
}
