package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 경기별 일정 설정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchScheduleUpdateRequest {

    private Long matchId;
    private LocalDateTime scheduledAt;
    private String venueName;
}
