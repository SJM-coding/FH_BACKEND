package com.futsal.user.presentation.dto;

import lombok.Data;

/**
 * 사용자 프로필 업데이트 요청 DTO
 */
@Data
public class UserUpdateRequest {
    private String nickname;
    private String profileImageUrl;
}
