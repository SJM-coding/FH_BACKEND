package com.futsal.tournament.dto;

import com.futsal.tournament.domain.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 대회 신청서 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {

    private Long id;
    private Long tournamentId;
    private String tournamentTitle;
    private Long teamId;
    private String teamName;
    private String teamLogoUrl;
    private Long applicantId;
    private String applicantName;
    private ApplicationStatus status;
    private Map<String, String> formData;
    private String message;
    private String rejectReason;
    private LocalDateTime appliedAt;
    private LocalDateTime processedAt;
}
