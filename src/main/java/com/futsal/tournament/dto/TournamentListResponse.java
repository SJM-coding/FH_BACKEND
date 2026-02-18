package com.futsal.tournament.dto;

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
    private String gender; // 성별 (MALE, FEMALE)
    private String playerType; // 선출 구분 (NON_PRO, PRO)
}
