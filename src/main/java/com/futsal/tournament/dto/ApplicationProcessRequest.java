package com.futsal.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 신청서 처리 요청 DTO (거부 시)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationProcessRequest {

    /**
     * 거부 사유 (거부 시 필수)
     */
    private String rejectReason;
}
