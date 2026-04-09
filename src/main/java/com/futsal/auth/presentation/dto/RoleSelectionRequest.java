package com.futsal.auth.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할 선택 요청 DTO
 */
@Getter
@NoArgsConstructor
public class RoleSelectionRequest {

    @NotNull(message = "역할은 필수입니다")
    @Pattern(regexp = "^(ORGANIZER|PARTICIPANT)$", message = "역할은 ORGANIZER 또는 PARTICIPANT만 가능합니다")
    private String role;
}
