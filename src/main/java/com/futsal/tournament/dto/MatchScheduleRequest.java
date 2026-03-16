package com.futsal.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 경기 일정 설정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchScheduleRequest {

    private LocalDateTime scheduledAt;
}
