package com.futsal.user.domain;

/**
 * 개최자 인증 상태
 */
public enum VerificationStatus {
    NONE,       // 미신청
    PENDING,    // 심사 중
    VERIFIED,   // 인증 완료
    REJECTED    // 거절됨
}
