package com.futsal.tournament.domain;

/**
 * 대회 신청 상태
 */
public enum ApplicationStatus {
    PENDING("승인 대기"),
    APPROVED("승인됨"),
    REJECTED("거부됨"),
    CANCELLED("취소됨");

    private final String description;

    ApplicationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean canCancel() {
        return this == PENDING || this == APPROVED;
    }

    public boolean canApprove() {
        return this == PENDING;
    }

    public boolean canReject() {
        return this == PENDING;
    }
}
