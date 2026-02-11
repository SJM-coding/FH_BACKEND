package com.futsal.tournament.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 대회 신청 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreateRequest {

    @NotNull(message = "팀 ID는 필수입니다")
    private Long teamId;

    /**
     * 신청 양식 데이터 (필드명: 값)
     */
    private Map<String, String> formData = new HashMap<>();

    /**
     * 신청 메시지 (선택)
     */
    private String message;
}
