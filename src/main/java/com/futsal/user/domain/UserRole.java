package com.futsal.user.domain;

/**
 * 사용자 권한
 */
public enum UserRole {
    PARTICIPANT,  // 참가자 - 대회 참가만 가능
    ORGANIZER,    // 개최자 - 대회 생성/관리 가능
    ADMIN         // 관리자 - 전체 권한
}
